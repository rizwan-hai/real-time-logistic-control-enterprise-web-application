package com.example.logistics.tracking;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import com.example.logistics.identity.config.IdentityProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration @EnableWebSocketMessageBroker
public class TrackingWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final IdentityProperties properties;
    private final JdbcTemplate db;
    private final JwtDecoder jwtDecoder;private final Converter<org.springframework.security.oauth2.jwt.Jwt,AbstractAuthenticationToken> converter;
    public TrackingWebSocketConfiguration(IdentityProperties properties,JdbcTemplate db,JwtDecoder jwtDecoder,Converter<org.springframework.security.oauth2.jwt.Jwt,AbstractAuthenticationToken> converter){this.properties=properties;this.db=db;this.jwtDecoder=jwtDecoder;this.converter=converter;}
    @Override public void registerStompEndpoints(StompEndpointRegistry registry){registry.addEndpoint("/ws/tracking").setAllowedOrigins(properties.appBaseUrl());}
    @Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.enableSimpleBroker("/topic");registry.setApplicationDestinationPrefixes("/app");}
    @Override public void configureClientInboundChannel(ChannelRegistration registration){registration.interceptors(new ChannelInterceptor(){
        @Override public Message<?> preSend(Message<?> message, MessageChannel channel){
            var a=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
            if(a!=null&&a.getCommand()==StompCommand.CONNECT){String auth=a.getFirstNativeHeader("Authorization");if(auth==null||!auth.startsWith("Bearer "))throw new AccessDeniedException("Bearer token is required for tracking websocket");try{var token=jwtDecoder.decode(auth.substring(7));if(!"access".equals(token.getClaimAsString("purpose")))throw new AccessDeniedException("Access token required");a.setUser(converter.convert(token));}catch(RuntimeException ex){throw new AccessDeniedException("Invalid tracking websocket token",ex);}}
            if(a!=null&&a.getCommand()==StompCommand.SUBSCRIBE){String d=a.getDestination();var user=a.getUser();
                if(!(user instanceof JwtAuthenticationToken jwt)||d==null)throw new AccessDeniedException("Tracking subscription is not authorized");
                boolean allowed=jwt.getAuthorities().stream().anyMatch(x->x.getAuthority().matches("ROLE_(PLATFORM_ADMIN|ORGANIZATION_ADMIN|OPERATIONS_MANAGER|TRANSPORT_DISPATCHER)"));
                if(!allowed)throw new AccessDeniedException("Tracking permission is required");
                String org=jwt.getToken().getClaimAsString("organizationId");
                if(d.equals("/topic/tracking/vehicles/"+org))return message;
                if(d.equals("/topic/tracking/alerts/"+org))return message;
                if(d.matches("/topic/tracking/trips/[0-9a-fA-F-]{36}")){
                    String trip=d.substring(d.lastIndexOf('/')+1);
                    if(db.queryForObject("SELECT COUNT(*) FROM trip WHERE id=? AND organization_id=?",Integer.class,trip,org)>0)return message;
                }
                throw new AccessDeniedException("Tracking subscription is outside your organization");
            }
            return message;
        }
    });}
}

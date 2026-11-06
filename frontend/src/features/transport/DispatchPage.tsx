import { useState } from 'react';
import { Alert, Button, Card, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/apiClient';
import { apiPath } from '@/services/apiVersion';
import { DndContext, useDraggable, useDroppable, type DragEndEvent } from '@dnd-kit/core';
import { CSS } from '@dnd-kit/utilities';
import type { ReactNode } from 'react';

type View = Record<string, any>;
function payload<T>(value: any): T { const result=value?.data?.data??value?.data??value; return (result?.content??result?.items??result) as T; }
function ShipmentCard({ shipment }: { shipment: View }) {
  const drag=useDraggable({id:shipment.id});
  return <div ref={drag.setNodeRef} {...drag.listeners} {...drag.attributes} style={{transform:CSS.Translate.toString(drag.transform),padding:8,margin:4,border:'1px solid #d9d9d9',borderRadius:6,cursor:'grab',background:'#fff'}}>{shipment.shipmentNumber}</div>;
}
function AssignmentDropZone({ children }: { children: ReactNode }) {
  const drop=useDroppable({id:'trip-assignment'});
  return <div ref={drop.setNodeRef} style={{minHeight:52,padding:8,border:'1px dashed #8c8c8c',borderRadius:6,background:drop.isOver?'#e6f4ff':'transparent'}}>{children}</div>;
}

export function DispatchPage() {
  const client = useQueryClient(); const [open,setOpen]=useState(false); const [driverId,setDriver]=useState<string>(); const [vehicleId,setVehicle]=useState<string>(); const [shipmentIds,setShipments]=useState<string[]>([]); const [tripId,setTrip]=useState<string>();
  const trips=useQuery({queryKey:['transport','trips'],queryFn:async()=>payload<View[]>((await apiClient.get(apiPath('transport/trips'))).data)});
  const shipments=useQuery({queryKey:['shipments'],queryFn:async()=>payload<View[]>((await apiClient.get(apiPath('shipments'))).data)});
  const drivers=useQuery({queryKey:['master-data','drivers'],queryFn:async()=>payload<View[]>((await apiClient.get(apiPath('master-data/drivers'),{params:{status:'ACTIVE',size:100}})).data)});
  const vehicles=useQuery({queryKey:['master-data','vehicles'],queryFn:async()=>payload<View[]>((await apiClient.get(apiPath('master-data/vehicles'),{params:{status:'ACTIVE',size:100}})).data)});
  const refresh=()=>{void client.invalidateQueries({queryKey:['transport']});void client.invalidateQueries({queryKey:['shipments']});};
  const create=useMutation({mutationFn:async()=>payload<View>((await apiClient.post(apiPath('transport/trips'),{})).data),onSuccess:(trip)=>{setTrip(trip.id);refresh();}});
  const dispatch=useMutation({mutationFn:()=>apiClient.post(apiPath(`transport/trips/${tripId}/dispatch`),{driverId,vehicleId,shipmentIds}),onSuccess:()=>{setOpen(false);setShipments([]);refresh();}});
  const rows=trips.data??[]; const queue=(shipments.data??[]).filter(s=>['PLANNED','DISPATCHED'].includes(s.status));
  const onDragEnd=(event:DragEndEvent)=>{if(event.over?.id==='trip-assignment'&&typeof event.active.id==='string')setShipments(current=>current.includes(event.active.id as string)?current:[...current,event.active.id as string]);};
  return <><div className="page-title"><Typography.Title level={2}>Dispatch board</Typography.Title><Typography.Paragraph type="secondary">Plan trips, check current assignments, and dispatch available shipments. Route estimates use a nearest-neighbor and 2-opt heuristic.</Typography.Paragraph></div>
    {(trips.isError||shipments.isError||drivers.isError||vehicles.isError)&&<Alert type="error" showIcon message="Dispatch data could not be loaded" description={String(trips.error??shipments.error??drivers.error??vehicles.error)} />}
    <Space className="section-card"><Button type="primary" onClick={()=>{setTrip(undefined);setOpen(true);}}>Create trip and assign</Button><Button onClick={()=>setOpen(true)}>Assign to existing trip</Button></Space>
    <Card title="Trips" className="section-card"><Table rowKey="id" loading={trips.isLoading} dataSource={rows} pagination={{pageSize:8}} columns={[{title:'Trip',dataIndex:'tripNumber'},{title:'Status',dataIndex:'status',render:(s:string)=><Tag>{s}</Tag>},{title:'Driver',dataIndex:'driverId',render:(v:string)=>drivers.data?.find(d=>d.id===v)?.name??v??'Unassigned'},{title:'Vehicle',dataIndex:'vehicleId',render:(v:string)=>vehicles.data?.find(d=>d.id===v)?.name??v??'Unassigned'},{title:'Planned',dataIndex:'plannedStart',render:(v:string)=>v?new Date(v).toLocaleString():'—'}]} /></Card>
    <DndContext onDragEnd={onDragEnd}><Card title="Shipment queue" className="section-card"><Typography.Text type="secondary">Drag a shipment into the assignment area or select it in the form.</Typography.Text><div style={{display:'flex',flexWrap:'wrap'}}>{queue.map(shipment=><ShipmentCard key={shipment.id} shipment={shipment}/>)}</div><AssignmentDropZone><Typography.Text strong>Trip assignment</Typography.Text><div>{shipmentIds.map(id=>{const shipment=queue.find(item=>item.id===id);return shipment?<Tag key={id} closable onClose={()=>setShipments(current=>current.filter(value=>value!==id))}>{shipment.shipmentNumber}</Tag>:null;})}</div></AssignmentDropZone><Table rowKey="id" dataSource={queue} pagination={{pageSize:8}} columns={[{title:'Shipment',dataIndex:'shipmentNumber'},{title:'Status',dataIndex:'status',render:(s:string)=><Tag>{s}</Tag>},{title:'Created',dataIndex:'createdAt',render:(v:string)=>v?new Date(v).toLocaleDateString():'—'}]} /></Card></DndContext>
    <Modal title="Assign trip" open={open} onCancel={()=>setOpen(false)} onOk={async()=>{let id=tripId;if(!id){const result=await create.mutateAsync();id=result.id;setTrip(id);}if(driverId&&vehicleId)await dispatch.mutateAsync();}} confirmLoading={create.isPending||dispatch.isPending} okText="Dispatch">
      <Typography.Paragraph type="secondary">Assignments are checked against active trips. Driver and vehicle availability reflects server-side conflicts.</Typography.Paragraph>
      <Select allowClear className="section-card" style={{width:'100%'}} placeholder="Create a new trip or select a planned trip" value={tripId} onChange={setTrip} options={rows.filter(t=>['PLANNED','DISPATCHED'].includes(t.status)).map(t=>({value:t.id,label:`${t.tripNumber} · ${t.status}`}))}/>
      <Select className="section-card" style={{width:'100%'}} placeholder="Select driver" value={driverId} onChange={setDriver} options={(drivers.data??[]).map(d=>({value:d.id,label:d.name??d.code}))}/>
      <Select className="section-card" style={{width:'100%'}} placeholder="Select vehicle" value={vehicleId} onChange={setVehicle} options={(vehicles.data??[]).map(v=>({value:v.id,label:v.name??v.code}))}/>
      <Select mode="multiple" className="section-card" style={{width:'100%'}} placeholder="Select shipments" value={shipmentIds} onChange={setShipments} options={queue.map(s=>({value:s.id,label:s.shipmentNumber}))}/>
      {tripId&&<Alert type="info" message={`Assigning to trip ${tripId.slice(0,8)}`} />}{(create.isError||dispatch.isError)&&<Alert className="section-card" type="error" message={String(create.error??dispatch.error)}/>}
    </Modal>
  </>;
}

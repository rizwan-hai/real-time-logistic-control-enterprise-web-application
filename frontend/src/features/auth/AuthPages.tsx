import { useState, type FormEvent } from 'react';
import { Alert, Button, Card, Input, Typography } from 'antd';
import { zodResolver } from '@hookform/resolvers/zod';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authService } from './authService';
import { useAuthStore } from '@/app/authStore';
import { FormField } from '@/shared/components/FormField';
import type { AuthLoginRequest, LoginData, ProfileData } from '@/services/apiTypes';

const loginSchema = z.object({ identifier: z.string().trim().min(1), password: z.string().min(1) });
type LoginValues = z.infer<typeof loginSchema>;
const dataOf = <T,>(envelope: unknown): T => (envelope as { data: T }).data;

export function LoginPage() {
  const methods = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const setToken = useAuthStore((state) => state.setAccessToken);
  const setProfile = useAuthStore((state) => state.setProfile);

  async function submit(values: LoginValues) {
    setError(null); setBusy(true);
    try {
      const body: AuthLoginRequest = challenge
        ? { mfaChallenge: challenge, ...(/^\d{6}$/.test(code) ? { totpCode: code } : { recoveryCode: code }) }
        : { identifier: values.identifier, password: values.password, deviceName: navigator.userAgent.slice(0, 120) };
      const result = dataOf<LoginData>(await authService.login(body));
      if (result.mfaRequired) { setChallenge(result.mfaChallenge ?? null); return; }
      if (!result.accessToken) throw new Error('The API did not return an access token.');
      setToken(result.accessToken);
      const profile = dataOf<ProfileData>(await authService.profile());
      setProfile(profile);
      const preferredHome = profile.roles.includes('CUSTOMER_USER') ? '/app/customer' : profile.roles.includes('DRIVER') ? '/app/driver' : '/app/staff';
      const destination = (location.state as { from?: string } | null)?.from ?? preferredHome;
      navigate(destination, { replace: true });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : 'Sign in failed.');
    } finally { setBusy(false); }
  }

  return (
    <main className="auth-page" aria-labelledby="login-title">
      <Card className="auth-card" bordered={false}>
        <div className="brand-lockup"><span className="brand-mark">LC</span><Typography.Text strong>{t('appName')}</Typography.Text></div>
        <Typography.Title level={2} id="login-title">{challenge ? t('verifyIdentity', 'Verify your identity') : t('welcome')}</Typography.Title>
        <Typography.Paragraph type="secondary">{challenge ? t('mfaPrompt', 'Enter your authenticator or recovery code.') : t('loginPrompt', 'Sign in to continue to your workspace.')}</Typography.Paragraph>
        {error && <Alert className="block-gap" type="error" showIcon message={error} />}
        <FormProvider {...methods}>
          <form onSubmit={methods.handleSubmit(submit)} className="form-stack">
            {challenge ? (
              <label className="field-label">{t('authenticatorCode', 'Authenticator or recovery code')}<Input autoFocus autoComplete="one-time-code" value={code} onChange={(event) => setCode(event.target.value)} required /></label>
            ) : <>
              <FormField<LoginValues> name="identifier" label={t('emailOrPhone')} autoComplete="username" />
              <FormField<LoginValues> name="password" label={t('password')} type="password" autoComplete="current-password" />
            </>}
            <Button type="primary" htmlType="submit" size="large" loading={busy} block>{challenge ? t('verifySignIn', 'Verify and sign in') : t('signIn')}</Button>
          </form>
        </FormProvider>
        {!challenge && <Link to="/forgot-password">{t('forgotPassword')}</Link>}
      </Card>
    </main>
  );
}

export function ForgotPasswordPage() {
  const [identifier, setIdentifier] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null);
    try { await authService.forgotPassword({ identifier }); setSent(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to send the reset link.'); }
  }
  return <main className="auth-page"><Card className="auth-card" bordered={false}>
    <Typography.Title level={2}>{t('forgotPassword')}</Typography.Title>
    {sent ? <Alert type="success" showIcon message={t('resetLinkSent', 'If the account is eligible, a reset link has been sent.')} /> : <form onSubmit={submit} className="form-stack">
      {error && <Alert type="error" showIcon message={error} />}
      <label className="field-label">{t('emailOrPhone')}<Input value={identifier} onChange={(event) => setIdentifier(event.target.value)} required /></label>
      <Button type="primary" htmlType="submit">{t('sendResetLink', 'Send reset link')}</Button>
    </form>}
    <Link to="/login">{t('backToSignIn', 'Back to sign in')}</Link>
  </Card></main>;
}

export function ResetPasswordPage() {
  const token = new URLSearchParams(window.location.search).get('token') ?? '';
  const [password, setPassword] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(null);
    try { await authService.resetPassword({ token, newPassword: password }); setDone(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Unable to reset password.'); }
  }
  return <main className="auth-page"><Card className="auth-card" bordered={false}>
    <Typography.Title level={2}>{t('choosePassword', 'Choose a new password')}</Typography.Title>
    {done ? <Alert type="success" showIcon message={t('passwordUpdated', 'Password updated. Sign in again.')} /> : <form onSubmit={submit} className="form-stack">
      {error && <Alert type="error" showIcon message={error} />}
      <label className="field-label">{t('newPassword', 'New password')}<Input.Password minLength={12} value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
      <Button type="primary" htmlType="submit" disabled={!token}>{t('updatePassword', 'Update password')}</Button>
    </form>}
  </Card></main>;
}

export function AccessDeniedPage() {
  const { t } = useTranslation();
  return <main className="center-page"><Card><Typography.Title level={2}>{t('accessDenied')}</Typography.Title><Typography.Paragraph>{t('noPermission', 'Your account does not have permission to view this page.')}</Typography.Paragraph><Link to="/app/staff">{t('backToWorkspace', 'Back to workspace')}</Link></Card></main>;
}


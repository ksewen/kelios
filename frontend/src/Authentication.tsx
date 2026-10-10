import { errorMessage } from './i18n';
import { useTranslation } from 'react-i18next';
import { useEffect, useState, type FormEvent } from 'react';
import { authSession as session, type Identity } from './auth';
import { Button } from './components/ui/button';

import { InventoryApp } from './InventoryApp';
export function Authentication() { const { t } = useTranslation();
  const [user, setUser] = useState<Identity | null>(null);
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState('');
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const initialTenant = /^\/login\/tenant\/([0-9a-f-]+)$/i.exec(window.location.pathname)?.[1] ?? '';
  const [tenant, setTenant] = useState(initialTenant);
  useEffect(() => {
    let alive = true;
    if (window.location.pathname === '/auth/failed') {
      setMessage('ui.tenantFailure'); setBusy(false); return;
    }
    void session.refresh().then((identity) => { if (alive) { setUser(identity); setBusy(false); } });
    return () => { alive = false; };
  }, []);
  async function local(event: FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    try { setUser(await session.login(login, password)); setPassword(''); }
    catch (error) { setMessage(error && typeof error === 'object' && 'code' in error ? String(error.code) : 'REQUEST_FAILED'); setPassword(''); }
    finally { setBusy(false); }
  }
  async function oidc(event: FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    try { window.location.assign(await session.startTenant(tenant)); }
    catch (error) { setMessage(error && typeof error === 'object' && 'code' in error ? String(error.code) : 'REQUEST_FAILED'); setBusy(false); }
  }
  async function logout() {
    setBusy(true); setUser(null); setMessage('');
    try { await session.logout(); } catch (error) { setMessage(error && typeof error === 'object' && 'code' in error ? String(error.code) : 'REQUEST_FAILED'); }
    finally { setBusy(false); }
  }
  async function refresh() {
    setBusy(true); const identity = await session.refresh(); setUser(identity);
    if (!identity) setMessage('ui.expiredHelp'); setBusy(false);
  }
  return <section aria-label={t('ui.authentication')} className="mt-8 rounded-xl border border-foreground/10 p-6">
    <h2 className="font-medium">{t('ui.accountAccess')}</h2>
    {message && <p role="alert" className="mt-3">{message.startsWith('ui.') ? t(message) : errorMessage(message)}</p>}
    {user ? <div className="mt-4 space-y-4">
      <p>{t('common.signedIn', { name: user.displayName })}</p>
      {user.status === 'PENDING' ? <p>{t('ui.pendingHelp')}</p>
        : <InventoryApp key={user.id} user={user} />}
      <div className="flex gap-3"><Button disabled={busy} onClick={refresh}>{t('ui.refreshAccountStatus')}</Button><Button disabled={busy} onClick={logout}>{t('ui.signOut')}</Button></div>
    </div> : <div className="mt-4 grid gap-6 md:grid-cols-2">
      <form onSubmit={local} className="space-y-3" aria-label={t('ui.globalLogin')}>
        <h3>{t('ui.globalAccount')}</h3>
        <label className="block">{t('ui.loginIdentifier')}<input className="mt-1 block w-full rounded border p-2" value={login} onChange={(event) => setLogin(event.target.value)} autoComplete="username" required maxLength={64} /></label>
        <label className="block">{t('ui.password')}<input type="password" className="mt-1 block w-full rounded border p-2" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>
        <Button disabled={busy} type="submit">{t('ui.signIn')}</Button>
      </form>
      <form onSubmit={oidc} className="space-y-3" aria-label={t('ui.tenantLogin')}>
        <h3>{t('ui.tenantIServAccount')}</h3>
        <label className="block">{t('ui.tenantID')}<input className="mt-1 block w-full rounded border p-2" value={tenant} onChange={(event) => setTenant(event.target.value)} required /></label>
        <Button disabled={busy} type="submit">{t('ui.signInWithIServ')}</Button>
      </form>
    </div>}
  </section>;
}

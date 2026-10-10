import { selectLanguage, i18n } from './i18n';
import { useTranslation } from 'react-i18next';
import { useState } from 'react';
import type { Branding } from './branding';
import { Authentication } from './Authentication';
import { Button } from './components/ui/button';

export function App({ branding }: { branding: Branding }) { const { t } = useTranslation();
  const [logoFailed, setLogoFailed] = useState(false);
  const [status, setStatus] = useState('ui.readyToConnect');
  const [checking, setChecking] = useState(false);
  async function checkApi() {
    setChecking(true);
    try {
      const response = await fetch('/api/v1/status', { signal: AbortSignal.timeout(5000) });
      const data = await response.json();
      if (!response.ok || data.application !== 'KELIOS' || data.status !== 'ready') throw new Error();
      setStatus('ui.backendConnected');
    } catch { setStatus('ui.backendUnavailable'); }
    finally { setChecking(false); }
  }
  return <div className="min-h-screen bg-background text-foreground">
    <header className="border-b border-foreground/10 px-6 py-5 md:px-12">
      <div className="mx-auto flex max-w-5xl items-center justify-between">
        {branding.logoUrl && !logoFailed
          ? <img src={branding.logoUrl} alt={t('ui.keliosLogo')} className="h-10 max-w-48 object-contain" onError={() => setLogoFailed(true)} />
          : <span className="text-xl font-semibold tracking-[0.18em]">KELIOS</span>}
        <span className="rounded-full bg-accent px-3 py-1 text-xs font-medium text-accent-foreground">{t('ui.inventory')}</span>
      </div>
    </header>
    <main className="mx-auto max-w-5xl px-6 py-16 md:py-24">
      <p className="mb-4 text-sm font-semibold uppercase tracking-widest text-primary">{t('ui.itAssetManagement')}</p>
      <h1 className="max-w-2xl text-4xl font-semibold leading-tight tracking-tight md:text-5xl">{t('ui.tagline')}</h1>
      <p className="mt-6 max-w-xl text-lg leading-relaxed opacity-75">{t('ui.workspaceHelp')}</p>
      <section aria-label={t('ui.connection')} className="mt-12 flex flex-wrap items-center gap-5 rounded-xl border border-foreground/10 bg-white/40 p-6">
        <div className="flex-1"><h2 className="font-medium">{t('ui.serviceConnection')}</h2><p role="status" className="mt-1 text-sm opacity-75">{t(status)}</p></div>
        <Button disabled={checking} onClick={checkApi}>{checking ? t('ui.checking') : t('ui.checkConnection')}</Button>
      </section>
      <label>{t('common.language')}<select aria-label={t('common.language')} value={i18n.language} onChange={e => void selectLanguage(e.target.value === 'de' ? 'de' : 'en')}><option value="en">English</option><option value="de">Deutsch</option></select></label><Authentication />
    </main>
  </div>;
}

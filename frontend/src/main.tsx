import { createRoot } from 'react-dom/client';
import { App } from './App';
import { applyBranding, loadBranding } from './branding';
import './styles.css';

const branding = await loadBranding();
applyBranding(branding);
createRoot(document.getElementById('root')!).render(<App branding={branding} />);

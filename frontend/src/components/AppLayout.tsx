import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { config } from '../config';
import { NotificationBell } from './NotificationBell';

const navItems = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/projects', label: 'Projects' },
  { to: '/issues', label: 'Issues' },
  { to: '/runs', label: 'Fix Runs' },
  { to: '/settings', label: 'Settings' },
];

export function AppLayout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
          <button
            onClick={() => navigate('/')}
            className="text-lg font-semibold tracking-tight text-slate-900"
          >
            TechDebt Cleanup
          </button>
          <div className="flex items-center gap-4">
            {config.devBypass && (
              <span className="badge bg-amber-100 text-amber-800">DEV BYPASS</span>
            )}
            <NotificationBell />
            <span className="text-sm text-slate-600">{user?.email ?? user?.upn}</span>
            {!config.devBypass && (
              <button onClick={() => signOut()} className="btn-secondary">
                Sign out
              </button>
            )}
          </div>
        </div>
        <nav className="mx-auto max-w-7xl px-6">
          <ul className="flex gap-6">
            {navItems.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `inline-block border-b-2 py-3 text-sm transition ${
                      isActive
                        ? 'border-brand-600 font-medium text-brand-700'
                        : 'border-transparent text-slate-600 hover:text-slate-900'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <main className="mx-auto max-w-7xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}

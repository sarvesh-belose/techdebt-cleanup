import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { RequireAuth } from './components/RequireAuth';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProjectListPage } from './pages/ProjectListPage';
import { ProjectCreatePage } from './pages/ProjectCreatePage';
import { ProjectDetailPage } from './pages/ProjectDetailPage';
import { IssuesPage } from './pages/IssuesPage';
import { FixRunsPage } from './pages/FixRunsPage';
import { FixRunDetailPage } from './pages/FixRunDetailPage';
import { PickProjectPage } from './pages/PickProjectPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { SettingsPage } from './pages/SettingsPage';

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'projects', element: <ProjectListPage /> },
      { path: 'projects/new', element: <ProjectCreatePage /> },
      { path: 'projects/:id', element: <ProjectDetailPage /> },
      { path: 'projects/:id/issues', element: <IssuesPage /> },
      { path: 'projects/:id/runs', element: <FixRunsPage /> },
      { path: 'projects/:id/runs/:runId', element: <FixRunDetailPage /> },
      {
        path: 'issues',
        element: (
          <PickProjectPage
            title="Issues"
            subroute="issues"
            description="Select a project to view its issue inbox."
          />
        ),
      },
      {
        path: 'runs',
        element: (
          <PickProjectPage
            title="Fix Runs"
            subroute="runs"
            description="Select a project to view its fix run history."
          />
        ),
      },
      { path: 'notifications', element: <NotificationsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);

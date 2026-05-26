import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listProjects } from '../api/projects';
import { ErrorBanner } from '../components/ErrorBanner';

interface Props {
  title: string;
  subroute: 'issues' | 'runs';
  description: string;
}

export function PickProjectPage({ title, subroute, description }: Props) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['projects', { includeArchived: false }],
    queryFn: () => listProjects({ includeArchived: false, size: 100 }),
  });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">{title}</h1>
        <p className="mt-1 text-sm text-slate-600">{description}</p>
      </div>
      <ErrorBanner error={error} />
      {isLoading && <div className="card text-slate-500">Loading…</div>}
      {data && data.totalElements === 0 && (
        <div className="card text-sm text-slate-500">
          No projects yet — register one first.
        </div>
      )}
      {data && data.totalElements > 0 && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {data.content.map((p) => (
            <Link
              key={p.id}
              to={`/projects/${p.id}/${subroute}`}
              className="card transition hover:border-brand-400 hover:shadow"
            >
              <div className="font-medium">{p.name}</div>
              {p.description && (
                <p className="mt-1 line-clamp-2 text-sm text-slate-600">{p.description}</p>
              )}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

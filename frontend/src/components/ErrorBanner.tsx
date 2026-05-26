import axios from 'axios';

interface Props {
  error: unknown;
  className?: string;
}

export function ErrorBanner({ error, className }: Props) {
  if (!error) return null;
  let message = 'Something went wrong';
  let details: Record<string, string> | null = null;

  if (axios.isAxiosError(error) && error.response?.data) {
    const data = error.response.data as Record<string, unknown>;
    if (typeof data.message === 'string') message = data.message;
    if (data.errors && typeof data.errors === 'object') {
      details = data.errors as Record<string, string>;
    }
  } else if (error instanceof Error) {
    message = error.message;
  }

  return (
    <div className={`rounded-md border border-red-200 bg-red-50 p-4 ${className ?? ''}`}>
      <div className="text-sm font-medium text-red-800">{message}</div>
      {details && (
        <ul className="mt-2 list-disc pl-5 text-sm text-red-700">
          {Object.entries(details).map(([field, msg]) => (
            <li key={field}>
              <span className="font-medium">{field}:</span> {msg}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

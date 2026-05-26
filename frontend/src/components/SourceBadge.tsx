import type { IssueSource } from '../api/types';

const labels: Record<IssueSource, string> = {
  VERACODE_SCA: 'Veracode SCA',
  VERACODE_SAST: 'Veracode SAST',
  SONARQUBE: 'SonarQube',
  JUNIT_COVERAGE: 'Coverage',
};

const styles: Record<IssueSource, string> = {
  VERACODE_SCA: 'bg-purple-100 text-purple-800',
  VERACODE_SAST: 'bg-indigo-100 text-indigo-800',
  SONARQUBE: 'bg-cyan-100 text-cyan-800',
  JUNIT_COVERAGE: 'bg-teal-100 text-teal-800',
};

export function SourceBadge({ source }: { source: IssueSource }) {
  return <span className={`badge ${styles[source]}`}>{labels[source]}</span>;
}

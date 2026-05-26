export type IssueSource = 'VERACODE_SCA' | 'VERACODE_SAST' | 'SONARQUBE' | 'JUNIT_COVERAGE';
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type IssueStatus = 'OPEN' | 'IN_PROGRESS' | 'FIXED' | 'REJECTED' | 'WONT_FIX' | 'FIXED_EXTERNALLY';
export type FixRunState =
  | 'QUEUED'
  | 'CLONING'
  | 'FIXING'
  | 'FAST_BUILD'
  | 'CANONICAL_BUILD'
  | 'PR_OPENED'
  | 'MERGED'
  | 'FAILED'
  | 'ABORTED'
  | 'SAFETY_BLOCKED';

export const ISSUE_SOURCES: IssueSource[] = [
  'VERACODE_SCA',
  'VERACODE_SAST',
  'SONARQUBE',
  'JUNIT_COVERAGE',
];
export const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
export const ISSUE_STATUSES: IssueStatus[] = [
  'OPEN',
  'IN_PROGRESS',
  'FIXED',
  'REJECTED',
  'WONT_FIX',
  'FIXED_EXTERNALLY',
];

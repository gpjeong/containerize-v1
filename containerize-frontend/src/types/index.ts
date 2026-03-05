export type Language = 'python' | 'nodejs' | 'java';
export type PythonFramework = 'fastapi' | 'flask' | 'django' | 'generic';
export type NodeJSFramework = 'express' | 'nestjs' | 'nextjs' | 'generic';

export interface ProjectInfo {
  language: string;
  framework: string;
  detected_version?: string;
  build_tool?: string;
  main_class?: string;
  dependencies: string[];
  metadata: Record<string, string>;
}

export interface BaseDockerConfig {
  language: Language;
  framework: string;
  runtime_version?: string;
  port: number;
  environment_vars: Record<string, string>;
  health_check_path: string | null;
  base_image: string;
  user: string;
  system_dependencies: string[];
  service_url?: string;
  custom_start_command?: string;
}

export interface PythonConfig extends BaseDockerConfig {
  language: 'python';
  package_manager?: 'pip' | 'poetry';
  server?: 'uvicorn' | 'gunicorn';
  requirements_content?: string;
  entrypoint_file?: string;
}

export interface NodeJSConfig extends BaseDockerConfig {
  language: 'nodejs';
  package_manager?: 'npm' | 'yarn' | 'pnpm';
  package_json?: Record<string, any>;
  build_command?: string;
  start_command?: string;
}

export interface JavaConfig extends BaseDockerConfig {
  language: 'java';
  build_tool?: 'maven' | 'gradle' | 'jar';
  jar_file_name?: string;
  main_class?: string;
  jvm_options?: string;
}

export type DockerConfig = PythonConfig | NodeJSConfig | JavaConfig;

export interface GenerateRequest {
  project_info?: ProjectInfo | null;
  config: Record<string, any>;
}

export interface GenerateResponse {
  dockerfile: string;
  session_id: string;
  metadata: Record<string, string>;
}

export interface UploadResponse {
  session_id: string;
  filename: string;
  project_info: ProjectInfo;
}

export interface JenkinsBuildRequest {
  config: Record<string, any>;
  jenkins_url: string;
  jenkins_job: string;
  jenkins_token: string;
  jenkins_username: string;
  git_url: string;
  git_branch: string;
  git_credential_id?: string | null;
  image_name: string;
  image_tag: string;
  use_kubernetes: boolean;
  use_kaniko: boolean;
  harbor_url?: string | null;
  harbor_credential_id?: string | null;
}

export interface JenkinsBuildResponse {
  job_name: string;
  queue_id?: string;
  queue_url: string;
  job_url: string;
  build_number?: number;
  build_url?: string;
  status: string;
  message: string;
}

export interface PipelinePreviewResponse {
  pipeline_script: string;
  dockerfile: string;
}

export interface AlertState {
  isOpen: boolean;
  message: string;
  type: 'error' | 'success';
  isHtml: boolean;
}

export interface AppState {
  currentLanguage: Language | null;
  currentSessionId: string | null;
  currentJarFileName: string | null;
  dockerfileContent: string;
  currentStep: number;
}

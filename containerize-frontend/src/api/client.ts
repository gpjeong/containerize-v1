import axios from 'axios';
import type {
  GenerateRequest,
  GenerateResponse,
  UploadResponse,
  JenkinsBuildRequest,
  JenkinsBuildResponse,
  PipelinePreviewResponse,
} from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const uploadJavaArtifact = async (file: File): Promise<UploadResponse> => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post<UploadResponse>('/upload/java', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
};

export const generateDockerfile = async (
  request: GenerateRequest
): Promise<GenerateResponse> => {
  const { data } = await api.post<GenerateResponse>('/generate', request);
  return data;
};

export const previewPipeline = async (
  request: JenkinsBuildRequest
): Promise<PipelinePreviewResponse> => {
  const { data } = await api.post<PipelinePreviewResponse>(
    '/preview/pipeline',
    request
  );
  return data;
};

export const triggerJenkinsBuild = async (
  request: JenkinsBuildRequest
): Promise<JenkinsBuildResponse> => {
  const { data } = await api.post<JenkinsBuildResponse>(
    '/build/jenkins',
    request
  );
  return data;
};

export const triggerJenkinsCustomBuild = async (request: {
  jenkins_url: string;
  jenkins_job: string;
  jenkins_token: string;
  jenkins_username: string;
  pipeline_script: string;
}): Promise<JenkinsBuildResponse> => {
  const { data } = await api.post<JenkinsBuildResponse>(
    '/build/jenkins/custom',
    request
  );
  return data;
};

export const checkJenkinsJob = async (request: {
  jenkins_url: string;
  jenkins_username: string;
  jenkins_token: string;
  job_name: string;
}): Promise<{ exists: boolean; job_name: string; job_url: string }> => {
  const { data } = await api.post('/setup/jenkins/check-job', request);
  return data;
};

export const createJenkinsJob = async (request: {
  jenkins_url: string;
  jenkins_username: string;
  jenkins_token: string;
  job_name: string;
  description?: string;
}): Promise<{ job_name: string; job_url: string; status: string; message: string }> => {
  const { data } = await api.post('/setup/jenkins/create-job', request);
  return data;
};

export const checkHarborProject = async (request: {
  harbor_url: string;
  harbor_username: string;
  harbor_password: string;
  project_name: string;
}): Promise<{ exists: boolean; project_name: string; project_url: string }> => {
  const { data } = await api.post('/setup/harbor/check-project', request);
  return data;
};

export const createHarborProject = async (request: {
  harbor_url: string;
  harbor_username: string;
  harbor_password: string;
  project_name: string;
  public?: boolean;
  enable_content_trust?: boolean;
  auto_scan?: boolean;
  severity?: string;
  prevent_vul?: boolean;
}): Promise<{
  project_name: string;
  project_url: string;
  status: string;
  message: string;
  settings: Record<string, any>;
}> => {
  const { data } = await api.post('/setup/harbor/create-project', request);
  return data;
};

export const getTemplates = async (): Promise<{ templates: Record<string, string[]> }> => {
  const { data } = await api.get('/templates');
  return data;
};

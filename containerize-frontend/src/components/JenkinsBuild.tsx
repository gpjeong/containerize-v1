import { useState } from 'react';
import { useApp } from '../context/AppContext';
import { previewPipeline, triggerJenkinsCustomBuild } from '../api/client';
import type { JenkinsBuildRequest } from '../types';
import PipelinePreviewModal from './modals/PipelinePreviewModal';
import JenkinsSetupModal from './modals/JenkinsSetupModal';
import HarborSetupModal from './modals/HarborSetupModal';

export default function JenkinsBuild() {
  const { state, dispatch } = useApp();
  const [enabled, setEnabled] = useState(false);

  // Jenkins fields
  const [jenkinsUrl, setJenkinsUrl] = useState('');
  const [jenkinsJob, setJenkinsJob] = useState('');
  const [jenkinsUsername, setJenkinsUsername] = useState('admin');
  const [jenkinsToken, setJenkinsToken] = useState('');

  // Git fields
  const [gitUrl, setGitUrl] = useState('');
  const [gitBranch, setGitBranch] = useState('main');
  const [gitCredentialId, setGitCredentialId] = useState('');

  // Docker image fields
  const [imageName, setImageName] = useState('');
  const [imageTag, setImageTag] = useState('latest');

  // Harbor fields
  const [harborUrl, setHarborUrl] = useState('');
  const [harborCredentialId, setHarborCredentialId] = useState('');

  // K8s/Kaniko
  const [useKubernetes, setUseKubernetes] = useState(false);
  const [useKaniko, setUseKaniko] = useState(false);

  // Modals
  const [showPipelineModal, setShowPipelineModal] = useState(false);
  const [pipelineScript, setPipelineScript] = useState('');
  const [showJenkinsSetup, setShowJenkinsSetup] = useState(false);
  const [showHarborSetup, setShowHarborSetup] = useState(false);

  if (!state.dockerfileContent) return null;

  const getDockerConfig = (): Record<string, any> | null => {
    if (typeof (window as any).__getDockerConfig === 'function') {
      return (window as any).__getDockerConfig();
    }
    return null;
  };

  const handlePreviewPipeline = async () => {
    if (!jenkinsUrl.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Jenkins URL을 입력해주세요.', type: 'error', isHtml: false } });
      return;
    }
    if (!jenkinsJob.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Jenkins Job 이름을 입력해주세요.', type: 'error', isHtml: false } });
      return;
    }
    if (!gitUrl.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Git Repository URL을 입력해주세요.', type: 'error', isHtml: false } });
      return;
    }
    if (!imageName.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Docker 이미지 이름을 입력해주세요.', type: 'error', isHtml: false } });
      return;
    }

    const config = getDockerConfig();
    if (!config) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Dockerfile 설정을 먼저 완료해주세요.', type: 'error', isHtml: false } });
      return;
    }

    dispatch({ type: 'SET_LOADING', payload: true });
    try {
      const payload: JenkinsBuildRequest = {
        config,
        jenkins_url: jenkinsUrl,
        jenkins_job: jenkinsJob,
        jenkins_token: 'dummy-token',
        jenkins_username: 'dummy-user',
        git_url: gitUrl,
        git_branch: gitBranch || 'main',
        git_credential_id: gitCredentialId || null,
        image_name: imageName,
        image_tag: imageTag || 'latest',
        use_kubernetes: useKubernetes,
        use_kaniko: useKaniko,
        harbor_url: harborUrl || null,
        harbor_credential_id: harborCredentialId || null,
      };

      const data = await previewPipeline(payload);
      setPipelineScript(data.pipeline_script);
      setShowPipelineModal(true);
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message || 'Pipeline 미리보기 실패';
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Pipeline 미리보기 실패: ' + msg, type: 'error', isHtml: false } });
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  };

  const handleBuildWithPipeline = async (editedScript: string) => {
    if (!jenkinsToken.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Jenkins API Token을 입력해주세요.', type: 'error', isHtml: false } });
      return;
    }

    setShowPipelineModal(false);
    dispatch({ type: 'SET_LOADING', payload: true });

    try {
      const data = await triggerJenkinsCustomBuild({
        jenkins_url: jenkinsUrl,
        jenkins_job: jenkinsJob,
        jenkins_token: jenkinsToken,
        jenkins_username: jenkinsUsername || 'admin',
        pipeline_script: editedScript,
      });

      const message = `
        <div class="space-y-4">
          <div class="text-lg font-semibold text-green-600">🚀 Jenkins 빌드가 시작되었습니다!</div>
          <div class="bg-gray-50 p-4 rounded-lg text-left space-y-2">
            <div class="flex items-center">
              <span class="font-medium text-gray-700 mr-2">Job Name:</span>
              <span class="text-gray-900 font-mono">${data.job_name}</span>
            </div>
            ${data.build_number ? `
            <div class="flex items-center">
              <span class="font-medium text-gray-700 mr-2">Build Number:</span>
              <span class="text-blue-600 font-mono font-semibold">#${data.build_number}</span>
            </div>` : data.queue_id ? `
            <div class="flex items-center">
              <span class="font-medium text-gray-700 mr-2">Queue ID:</span>
              <span class="text-orange-600 font-mono">#${data.queue_id}</span>
              <span class="text-xs text-gray-500 ml-2">(빌드 대기 중)</span>
            </div>` : ''}
          </div>
          <div class="pt-2">
            <a href="${data.job_url}" target="_blank" rel="noopener noreferrer"
               class="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors">
              Jenkins에서 빌드 확인하기
            </a>
          </div>
        </div>
      `;

      dispatch({ type: 'SHOW_ALERT', payload: { message, type: 'success', isHtml: true } });
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message || 'Jenkins 빌드 실패';
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Jenkins 빌드 실패: ' + msg, type: 'error', isHtml: false } });
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  };

  return (
    <>
      <div className="mt-8">
        <h2 className="text-2xl font-semibold mb-4">
          5. Jenkins를 통한 Docker Image 빌드 (선택사항)
        </h2>

        <div className="mb-4">
          <label className="flex items-center mb-2">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              className="mr-2"
            />
            <span className="text-sm font-medium text-gray-700">Jenkins에서 자동 빌드</span>
          </label>
          <p className="text-sm text-gray-500 ml-6">
            Git 저장소에서 소스를 가져와 Dockerfile 기반으로 Jenkins에서 이미지를 자동으로 빌드합니다
          </p>
        </div>

        {enabled && (
          <div>
            {/* Warning */}
            <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4 mb-6">
              <div className="flex">
                <div className="flex-shrink-0">
                  <svg className="h-5 w-5 text-yellow-400" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                  </svg>
                </div>
                <div className="ml-3">
                  <p className="text-sm text-yellow-700">
                    사전 준비: Jenkins에 빈 Pipeline Job 생성, API Token 발급, Git Credential 등록 필수
                  </p>
                </div>
              </div>
            </div>

            {/* Jenkins Server Settings */}
            <h3 className="text-lg font-semibold mb-3">Jenkins 서버 설정</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Jenkins URL <span className="text-red-500">*</span>
                </label>
                <input type="text" value={jenkinsUrl} onChange={(e) => setJenkinsUrl(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: http://jenkins.example.com:8080" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Jenkins Job 이름 <span className="text-red-500">*</span>
                </label>
                <input type="text" value={jenkinsJob} onChange={(e) => setJenkinsJob(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: dockerfile-builder" />
                <p className="mt-1 text-sm text-gray-500">미리 생성한 Pipeline Job 이름</p>
              </div>
            </div>

            {/* Jenkins Job Setup */}
            <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-md">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-blue-900">Jenkins Job이 없으신가요?</p>
                  <p className="text-xs text-blue-700 mt-1">아래 버튼으로 Pipeline Job을 자동 생성하세요</p>
                </div>
                <button onClick={() => setShowJenkinsSetup(true)} type="button"
                  className="px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700 transition">
                  🔧 Jenkins Job 생성
                </button>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Jenkins Username</label>
                <input type="text" value={jenkinsUsername} onChange={(e) => setJenkinsUsername(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="Jenkins username" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  API Token <span className="text-red-500">*</span>
                </label>
                <input type="password" value={jenkinsToken} onChange={(e) => setJenkinsToken(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="Jenkins API Token" />
              </div>
            </div>

            {/* Git Settings */}
            <h3 className="text-lg font-semibold mt-6 mb-3">Git 저장소 설정</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Git Repository URL <span className="text-red-500">*</span>
                </label>
                <input type="text" value={gitUrl} onChange={(e) => setGitUrl(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: https://github.com/user/repo.git" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Git Branch</label>
                <input type="text" value={gitBranch} onChange={(e) => setGitBranch(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="기본: main" />
              </div>
            </div>
            <div className="mt-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">Git Credential ID</label>
              <input type="text" value={gitCredentialId} onChange={(e) => setGitCredentialId(e.target.value)}
                className="w-full p-2 border border-gray-300 rounded-md" placeholder="Jenkins에 등록된 Credential ID (예: github-credentials)" />
            </div>

            {/* Docker Image Settings */}
            <h3 className="text-lg font-semibold mt-6 mb-3">Docker 이미지 설정</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Docker 이미지 이름 <span className="text-red-500">*</span>
                </label>
                <input type="text" value={imageName} onChange={(e) => setImageName(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: my-app" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Docker 이미지 태그</label>
                <input type="text" value={imageTag} onChange={(e) => setImageTag(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="기본: latest" />
              </div>
            </div>

            {/* Harbor Registry Settings */}
            <div className="mt-4 p-4 bg-purple-50 border border-purple-200 rounded-md">
              <h4 className="font-medium text-gray-700 mb-3 flex items-center">
                <span className="mr-2">🐳</span> Harbor Registry 설정
              </h4>
              <div className="mb-3">
                <label className="block text-sm font-medium text-gray-700 mb-2">Harbor Registry URL</label>
                <input type="text" value={harborUrl} onChange={(e) => setHarborUrl(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: harbor.example.com 또는 harbor.example.com/project" />
                <p className="text-xs text-gray-500 mt-1">비어있으면 로컬 빌드만 수행 (tar 파일 저장)</p>
              </div>

              {/* Harbor Project Setup */}
              <div className="mb-4 p-4 bg-green-50 border border-green-200 rounded-md">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-green-900">Harbor Project가 없으신가요?</p>
                    <p className="text-xs text-green-700 mt-1">아래 버튼으로 Harbor Project를 자동 생성하세요</p>
                  </div>
                  <button onClick={() => setShowHarborSetup(true)} type="button"
                    className="px-4 py-2 bg-green-600 text-white text-sm rounded-md hover:bg-green-700 transition">
                    🚢 Harbor Project 생성
                  </button>
                </div>
              </div>

              <div className="mb-3">
                <label className="block text-sm font-medium text-gray-700 mb-2">Jenkins Credential ID (Harbor 인증)</label>
                <input type="text" value={harborCredentialId} onChange={(e) => setHarborCredentialId(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: harbor-credentials" />
                <p className="text-xs text-gray-500 mt-1">Jenkins에 등록된 Docker Registry 인증 정보 ID</p>
              </div>
            </div>

            {/* Kubernetes Option */}
            <div className="mt-4 p-4 bg-blue-50 border border-blue-200 rounded-md">
              <label className="flex items-center cursor-pointer">
                <input type="checkbox" checked={useKubernetes}
                  onChange={(e) => { setUseKubernetes(e.target.checked); if (!e.target.checked) setUseKaniko(false); }}
                  className="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500" />
                <span className="ml-2 text-sm font-medium text-gray-700">
                  🚢 Kubernetes 환경용 Pipeline 생성 (Jenkins가 K8s에서 실행 중인 경우)
                </span>
              </label>
              <p className="mt-2 text-xs text-gray-600 ml-6">
                Jenkins가 Kubernetes 클러스터에서 실행 중이면 이 옵션을 선택하세요.
              </p>
              {useKubernetes && (
                <div className="mt-3 ml-6">
                  <label className="flex items-center cursor-pointer">
                    <input type="checkbox" checked={useKaniko}
                      onChange={(e) => setUseKaniko(e.target.checked)}
                      className="w-4 h-4 text-green-600 bg-gray-100 border-gray-300 rounded focus:ring-green-500" />
                    <span className="ml-2 text-sm font-medium text-gray-700">
                      🔧 Kaniko 사용 (권장 - privileged 모드 불필요)
                    </span>
                  </label>
                  <p className="mt-1 text-xs text-gray-600 ml-6">
                    Kaniko는 Docker daemon 없이 이미지를 빌드합니다. privileged 권한이 필요 없어 더 안전합니다.<br />
                    ⚠️ DinD에서 문제가 발생하면 Kaniko를 사용하세요.
                  </p>
                </div>
              )}
            </div>

            <div className="mt-6">
              <button onClick={handlePreviewPipeline}
                className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition flex items-center gap-2">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
                <span>Jenkins Image Build Pipeline 생성하기</span>
              </button>
            </div>
          </div>
        )}
      </div>

      <PipelinePreviewModal
        isOpen={showPipelineModal}
        pipelineScript={pipelineScript}
        onClose={() => setShowPipelineModal(false)}
        onBuild={handleBuildWithPipeline}
      />
      <JenkinsSetupModal
        isOpen={showJenkinsSetup}
        onClose={() => setShowJenkinsSetup(false)}
        initialUrl={jenkinsUrl}
        initialJobName={jenkinsJob}
        onJobCreated={(jobName) => setJenkinsJob(jobName)}
      />
      <HarborSetupModal
        isOpen={showHarborSetup}
        onClose={() => setShowHarborSetup(false)}
        initialUrl={harborUrl}
        onProjectCreated={(projectUrl) => setHarborUrl(projectUrl)}
      />
    </>
  );
}

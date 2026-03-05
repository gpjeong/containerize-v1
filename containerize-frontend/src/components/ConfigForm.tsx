import { useState } from 'react';
import { useApp } from '../context/AppContext';
import { generateDockerfile } from '../api/client';

export default function ConfigForm() {
  const { state, dispatch } = useApp();
  const { currentLanguage, currentJarFileName } = state;

  // Form state
  const [baseImage, setBaseImage] = useState('');
  const [port, setPort] = useState('');
  const [serviceUrl, setServiceUrl] = useState('');
  const [startCommand, setStartCommand] = useState('');

  // Optional toggles
  const [enableEnvVars, setEnableEnvVars] = useState(false);
  const [envVars, setEnvVars] = useState('');
  const [enableHealthCheck, setEnableHealthCheck] = useState(false);
  const [healthCheck, setHealthCheck] = useState('/health');
  const [enableSystemDeps, setEnableSystemDeps] = useState(false);
  const [systemDeps, setSystemDeps] = useState('');

  if (!currentLanguage) return null;

  const isJava = currentLanguage === 'java';
  const stepNumber = isJava ? 3 : 2;

  const getPlaceholders = () => {
    if (currentLanguage === 'python') {
      return {
        baseImage: '예: python:3.11-slim',
        port: '예: 8000',
        startCommand: '예: uvicorn main:app --host 0.0.0.0 --port 8000',
      };
    } else if (currentLanguage === 'nodejs') {
      return {
        baseImage: '예: node:20-alpine',
        port: '예: 3000',
        startCommand: '예: node server.js',
      };
    }
    return {
      baseImage: '예: eclipse-temurin:17-jre-alpine',
      port: '예: 8080',
      startCommand: '예: java -jar app.jar',
    };
  };

  const placeholders = getPlaceholders();

  const parseEnvVars = (text: string): Record<string, string> => {
    const vars: Record<string, string> = {};
    if (!text) return vars;
    text.split('\n').forEach((line) => {
      const trimmed = line.trim();
      if (trimmed && trimmed.includes('=')) {
        const [key, ...valueParts] = trimmed.split('=');
        vars[key.trim()] = valueParts.join('=').trim();
      }
    });
    return vars;
  };

  const parseSystemDeps = (text: string): string[] => {
    if (!text) return [];
    return text.trim().split(/\s+/).filter(Boolean);
  };

  const validate = (): boolean => {
    if (!baseImage.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Base Image를 입력해주세요.', type: 'error', isHtml: false } });
      return false;
    }
    if (!port.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: '포트를 입력해주세요.', type: 'error', isHtml: false } });
      return false;
    }
    if (!serviceUrl.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: '서비스 URL을 입력해주세요.', type: 'error', isHtml: false } });
      return false;
    }
    if (!startCommand.trim()) {
      dispatch({ type: 'SHOW_ALERT', payload: { message: '실행 명령어를 입력해주세요.', type: 'error', isHtml: false } });
      return false;
    }
    return true;
  };

  const buildConfig = (): Record<string, any> => {
    const config: Record<string, any> = {
      language: currentLanguage,
      framework: isJava ? 'spring-boot' : 'generic',
      runtime_version: '',
      port: parseInt(port),
      environment_vars: enableEnvVars ? parseEnvVars(envVars) : {},
      health_check_path: enableHealthCheck ? healthCheck : null,
      system_dependencies: enableSystemDeps ? parseSystemDeps(systemDeps) : [],
      base_image: baseImage,
      user: 'appuser',
      service_url: serviceUrl,
      custom_start_command: startCommand,
    };

    if (currentLanguage === 'python') {
      config.package_manager = 'pip';
      config.entrypoint_file = 'main.py';
    } else if (currentLanguage === 'nodejs') {
      config.package_manager = 'npm';
      config.start_command = startCommand;
    } else if (currentLanguage === 'java') {
      config.build_tool = 'jar';
      config.jar_file_name = currentJarFileName || 'app.jar';
      config.jvm_options = '-Xmx512m';
    }

    return config;
  };

  const handleGenerate = async () => {
    if (!validate()) return;

    dispatch({ type: 'SET_LOADING', payload: true });
    try {
      const config = buildConfig();

      const data = await generateDockerfile({ config });
      dispatch({ type: 'SET_SESSION_ID', payload: data.session_id });
      dispatch({ type: 'SET_DOCKERFILE', payload: data.dockerfile });
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message || 'Generation failed';
      dispatch({ type: 'SHOW_ALERT', payload: { message: 'Dockerfile 생성 실패: ' + msg, type: 'error', isHtml: false } });
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  };

  // Expose form values for Jenkins step
  const getConfigForJenkins = () => buildConfig();

  // Store getConfigForJenkins on window for cross-component access
  (window as any).__getDockerConfig = getConfigForJenkins;

  return (
    <div className="mt-8">
      <h2 className="text-2xl font-semibold mb-4">
        {stepNumber}. Dockerfile 세부 내용 설정
      </h2>

      {/* Required fields */}
      <div className="mt-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Base Image <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={baseImage}
          onChange={(e) => setBaseImage(e.target.value)}
          className="w-full p-2 border border-gray-300 rounded-md"
          placeholder={placeholders.baseImage}
        />
        <p className="mt-1 text-sm text-gray-500">사용할 Docker 베이스 이미지를 입력하세요</p>
      </div>

      <div className="mt-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          포트 <span className="text-red-500">*</span>
        </label>
        <input
          type="number"
          value={port}
          onChange={(e) => setPort(e.target.value)}
          className="w-full p-2 border border-gray-300 rounded-md"
          placeholder={placeholders.port}
        />
      </div>

      <div className="mt-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          서비스 URL <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={serviceUrl}
          onChange={(e) => setServiceUrl(e.target.value)}
          className="w-full p-2 border border-gray-300 rounded-md"
          placeholder="예: https://api.example.com"
        />
        <p className="mt-1 text-sm text-gray-500">서비스가 배포될 URL을 입력하세요</p>
      </div>

      <div className="mt-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          실행 명령어 <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={startCommand}
          onChange={(e) => setStartCommand(e.target.value)}
          className="w-full p-2 border border-gray-300 rounded-md"
          placeholder={placeholders.startCommand}
        />
        <p className="mt-1 text-sm text-gray-500">컨테이너 실행 명령어를 입력하세요</p>
      </div>

      {/* Optional: Environment Variables */}
      <div className="mt-4">
        <label className="flex items-center mb-2">
          <input
            type="checkbox"
            checked={enableEnvVars}
            onChange={(e) => setEnableEnvVars(e.target.checked)}
            className="mr-2"
          />
          <span className="text-sm font-medium text-gray-700">환경 변수 추가</span>
        </label>
        {enableEnvVars && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              환경 변수 (KEY=VALUE, 한 줄에 하나씩)
            </label>
            <textarea
              value={envVars}
              onChange={(e) => setEnvVars(e.target.value)}
              rows={3}
              className="w-full p-2 border border-gray-300 rounded-md font-mono text-sm"
              placeholder={`ENV=production\nDEBUG=false`}
            />
          </div>
        )}
      </div>

      {/* Optional: Health Check */}
      <div className="mt-4">
        <label className="flex items-center mb-2">
          <input
            type="checkbox"
            checked={enableHealthCheck}
            onChange={(e) => setEnableHealthCheck(e.target.checked)}
            className="mr-2"
          />
          <span className="text-sm font-medium text-gray-700">Health Check 추가</span>
        </label>
        {enableHealthCheck && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Health Check 경로
            </label>
            <input
              type="text"
              value={healthCheck}
              onChange={(e) => setHealthCheck(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md"
            />
          </div>
        )}
      </div>

      {/* Optional: System Dependencies */}
      <div className="mt-4">
        <label className="flex items-center mb-2">
          <input
            type="checkbox"
            checked={enableSystemDeps}
            onChange={(e) => setEnableSystemDeps(e.target.checked)}
            className="mr-2"
          />
          <span className="text-sm font-medium text-gray-700">시스템 의존성 패키지 추가</span>
        </label>
        {enableSystemDeps && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              시스템 의존성 패키지 (공백으로 구분)
            </label>
            <input
              type="text"
              value={systemDeps}
              onChange={(e) => setSystemDeps(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md"
              placeholder="예: curl wget"
            />
          </div>
        )}
      </div>

      <button
        onClick={handleGenerate}
        className="mt-6 px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition"
      >
        Dockerfile 생성
      </button>
    </div>
  );
}

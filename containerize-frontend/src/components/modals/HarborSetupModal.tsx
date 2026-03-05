import { useState } from 'react';
import { checkHarborProject as checkHarborProjectApi, createHarborProject as createHarborProjectApi } from '../../api/client';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  initialUrl: string;
  onProjectCreated: (projectUrl: string) => void;
}

export default function HarborSetupModal({ isOpen, onClose, initialUrl, onProjectCreated }: Props) {
  const [url, setUrl] = useState(initialUrl ? `https://${initialUrl.split('/')[0]}` : '');
  const [projectName, setProjectName] = useState('');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [isPublic, setIsPublic] = useState(false);
  const [autoScan, setAutoScan] = useState(true);
  const [contentTrust, setContentTrust] = useState(false);
  const [preventVul, setPreventVul] = useState(false);
  const [severity, setSeverity] = useState('high');
  const [status, setStatus] = useState<{ type: string; message: string } | null>(null);
  const [canCreate, setCanCreate] = useState(false);

  if (!isOpen) return null;

  const handleCheck = async () => {
    if (!url || !projectName || !username || !password) {
      setStatus({ type: 'error', message: '⚠️ 모든 필수 항목을 입력하세요' });
      return;
    }
    if (!/^[a-z0-9][a-z0-9_-]*$/.test(projectName)) {
      setStatus({ type: 'error', message: '⚠️ Project 이름은 소문자, 숫자, -, _만 사용 가능합니다' });
      return;
    }
    setStatus({ type: 'info', message: '🔍 Harbor Project 존재 확인 중...' });
    try {
      const data = await checkHarborProjectApi({ harbor_url: url, harbor_username: username, harbor_password: password, project_name: projectName });
      if (data.exists) {
        setStatus({ type: 'success', message: `✅ Project '${projectName}'이 이미 존재합니다` });
        setCanCreate(false);
      } else {
        setStatus({ type: 'warning', message: `📋 Project '${projectName}'이 존재하지 않습니다. 생성 버튼을 클릭하세요` });
        setCanCreate(true);
      }
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message;
      setStatus({ type: 'error', message: `❌ 확인 실패: ${msg}` });
      setCanCreate(false);
    }
  };

  const handleCreate = async () => {
    setCanCreate(false);
    setStatus({ type: 'info', message: '⚙️ Harbor Project 생성 중...' });
    try {
      const data = await createHarborProjectApi({
        harbor_url: url, harbor_username: username, harbor_password: password,
        project_name: projectName, public: isPublic, enable_content_trust: contentTrust,
        auto_scan: autoScan, severity, prevent_vul: preventVul,
      });
      const settingsInfo = [];
      if (isPublic) settingsInfo.push('Public');
      if (autoScan) settingsInfo.push('자동 스캔');
      if (contentTrust) settingsInfo.push('Content Trust');
      if (preventVul) settingsInfo.push('취약점 차단');
      setStatus({ type: 'success', message: `✅ Project 생성 성공! ${data.project_name} (${settingsInfo.join(', ') || '기본 설정'})` });
      const baseUrl = url.replace(/^https?:\/\//, '');
      onProjectCreated(`${baseUrl}/${projectName}`);
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message;
      setStatus({ type: 'error', message: `❌ 생성 실패: ${msg}` });
      setCanCreate(true);
    }
  };

  const handleClose = () => {
    setPassword('');
    setStatus(null);
    setCanCreate(false);
    onClose();
  };

  const statusBg = status?.type === 'error' ? 'bg-red-100' : status?.type === 'success' ? 'bg-green-100' : status?.type === 'warning' ? 'bg-yellow-100' : 'bg-blue-100';
  const statusColor = status?.type === 'error' ? 'text-red-600' : status?.type === 'success' ? 'text-green-600' : status?.type === 'warning' ? 'text-orange-600' : 'text-blue-600';

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" onClick={handleClose}>
      <div className="bg-white rounded-lg shadow-2xl max-w-2xl w-full max-h-[90vh] flex flex-col" onClick={(e) => e.stopPropagation()}>
        <div className="p-6 border-b border-gray-200 flex justify-between items-center">
          <div>
            <h3 className="text-xl font-bold text-gray-900">🚢 Harbor Project 생성</h3>
            <p className="text-sm text-gray-500 mt-1">Harbor 프로젝트를 자동으로 생성합니다</p>
          </div>
          <button onClick={handleClose} className="text-gray-400 hover:text-gray-600 transition">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-auto p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Harbor URL <span className="text-red-500">*</span></label>
            <input type="text" value={url} onChange={(e) => setUrl(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: https://harbor.example.com" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Project Name <span className="text-red-500">*</span></label>
            <input type="text" value={projectName} onChange={(e) => setProjectName(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: myproject (소문자, 숫자, -, _ 허용)" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Admin Username <span className="text-red-500">*</span></label>
            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Admin Password <span className="text-red-500">*</span></label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" placeholder="Harbor admin password" />
            <p className="text-xs text-gray-500 mt-1">💡 Project 생성 권한이 있는 관리자 계정이 필요합니다</p>
          </div>

          <div className="border-t pt-4">
            <h4 className="text-sm font-semibold text-gray-700 mb-3">프로젝트 설정</h4>
            <div className="space-y-3">
              <label className="flex items-center">
                <input type="checkbox" checked={isPublic} onChange={(e) => setIsPublic(e.target.checked)} className="mr-2" />
                <span className="text-sm text-gray-700">Public 프로젝트 (누구나 접근 가능)</span>
              </label>
              <label className="flex items-center">
                <input type="checkbox" checked={autoScan} onChange={(e) => setAutoScan(e.target.checked)} className="mr-2" />
                <span className="text-sm text-gray-700">이미지 Push 시 자동 취약점 스캔</span>
              </label>
              <label className="flex items-center">
                <input type="checkbox" checked={contentTrust} onChange={(e) => setContentTrust(e.target.checked)} className="mr-2" />
                <span className="text-sm text-gray-700">Content Trust 활성화 (이미지 서명)</span>
              </label>
              <label className="flex items-center">
                <input type="checkbox" checked={preventVul} onChange={(e) => setPreventVul(e.target.checked)} className="mr-2" />
                <span className="text-sm text-gray-700">취약점 있는 이미지 Pull 차단</span>
              </label>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">취약점 심각도 기준</label>
                <select value={severity} onChange={(e) => setSeverity(e.target.value)}
                  className="w-full p-2 border border-gray-300 rounded-md">
                  <option value="critical">Critical (치명적)</option>
                  <option value="high">High (높음)</option>
                  <option value="medium">Medium (중간)</option>
                  <option value="low">Low (낮음)</option>
                </select>
              </div>
            </div>
          </div>

          {status && (
            <div className={`p-3 rounded-md ${statusBg}`}>
              <span className={statusColor}>{status.message}</span>
            </div>
          )}
        </div>

        <div className="p-6 border-t border-gray-200 flex justify-end gap-3">
          <button onClick={handleClose} className="px-6 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 transition">취소</button>
          <button onClick={handleCheck} className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition">1️⃣ Project 존재 확인</button>
          <button onClick={handleCreate} disabled={!canCreate}
            className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition disabled:bg-gray-400 disabled:cursor-not-allowed">
            2️⃣ Project 생성하기
          </button>
        </div>
      </div>
    </div>
  );
}

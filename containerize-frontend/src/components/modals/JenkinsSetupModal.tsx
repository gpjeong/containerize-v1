import { useState } from 'react';
import { checkJenkinsJob as checkJenkinsJobApi, createJenkinsJob as createJenkinsJobApi } from '../../api/client';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  initialUrl: string;
  initialJobName: string;
  onJobCreated: (jobName: string) => void;
}

export default function JenkinsSetupModal({ isOpen, onClose, initialUrl, initialJobName, onJobCreated }: Props) {
  const [url, setUrl] = useState(initialUrl);
  const [jobName, setJobName] = useState(initialJobName);
  const [username, setUsername] = useState('admin');
  const [token, setToken] = useState('');
  const [description, setDescription] = useState('Auto-generated Pipeline job for containerization');
  const [status, setStatus] = useState<{ type: string; message: string } | null>(null);
  const [canCreate, setCanCreate] = useState(false);

  if (!isOpen) return null;

  const handleCheck = async () => {
    if (!url || !jobName || !username || !token) {
      setStatus({ type: 'error', message: '⚠️ 모든 필수 항목을 입력하세요' });
      return;
    }
    setStatus({ type: 'info', message: '🔍 Jenkins Job 존재 확인 중...' });
    try {
      const data = await checkJenkinsJobApi({ jenkins_url: url, jenkins_username: username, jenkins_token: token, job_name: jobName });
      if (data.exists) {
        setStatus({ type: 'success', message: `✅ Job '${jobName}'이 이미 존재합니다` });
        setCanCreate(false);
      } else {
        setStatus({ type: 'warning', message: `📋 Job '${jobName}'이 존재하지 않습니다. 생성 버튼을 클릭하세요` });
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
    setStatus({ type: 'info', message: '⚙️ Jenkins Job 생성 중...' });
    try {
      const data = await createJenkinsJobApi({
        jenkins_url: url, jenkins_username: username, jenkins_token: token,
        job_name: jobName, description,
      });
      setStatus({ type: 'success', message: `✅ Job 생성 성공! Job 이름: ${data.job_name}` });
      onJobCreated(jobName);
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message;
      setStatus({ type: 'error', message: `❌ 생성 실패: ${msg}` });
      setCanCreate(true);
    }
  };

  const handleClose = () => {
    setToken('');
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
            <h3 className="text-xl font-bold text-gray-900">🔧 Jenkins Job 생성</h3>
            <p className="text-sm text-gray-500 mt-1">Pipeline Job을 자동으로 생성합니다</p>
          </div>
          <button onClick={handleClose} className="text-gray-400 hover:text-gray-600 transition">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-auto p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Jenkins URL <span className="text-red-500">*</span></label>
            <input type="text" value={url} onChange={(e) => setUrl(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: http://jenkins.example.com:8080" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Job Name <span className="text-red-500">*</span></label>
            <input type="text" value={jobName} onChange={(e) => setJobName(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" placeholder="예: dockerfile-builder" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Admin Username <span className="text-red-500">*</span></label>
            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Admin API Token <span className="text-red-500">*</span></label>
            <input type="password" value={token} onChange={(e) => setToken(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" placeholder="Jenkins admin API token" />
            <p className="text-xs text-gray-500 mt-1">💡 Job 생성 권한이 있는 관리자 계정이 필요합니다</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Job Description (선택)</label>
            <input type="text" value={description} onChange={(e) => setDescription(e.target.value)}
              className="w-full p-2 border border-gray-300 rounded-md" />
          </div>
          {status && (
            <div className={`p-3 rounded-md ${statusBg}`}>
              <span className={statusColor}>{status.message}</span>
            </div>
          )}
        </div>

        <div className="p-6 border-t border-gray-200 flex justify-end gap-3">
          <button onClick={handleClose} className="px-6 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 transition">취소</button>
          <button onClick={handleCheck} className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition">1️⃣ Job 존재 확인</button>
          <button onClick={handleCreate} disabled={!canCreate}
            className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition disabled:bg-gray-400 disabled:cursor-not-allowed">
            2️⃣ Job 생성하기
          </button>
        </div>
      </div>
    </div>
  );
}

import { useState, useCallback } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { StreamLanguage } from '@codemirror/language';
import { dockerFile } from '@codemirror/legacy-modes/mode/dockerfile';
import { oneDark } from '@codemirror/theme-one-dark';
import { useApp } from '../context/AppContext';
import ResetConfirmModal from './modals/ResetConfirmModal';

export default function DockerfilePreview() {
  const { state, dispatch } = useApp();
  const [editorContent, setEditorContent] = useState('');
  const [showResetModal, setShowResetModal] = useState(false);

  const content = editorContent || state.dockerfileContent;

  const handleChange = useCallback((value: string) => {
    setEditorContent(value);
  }, []);

  if (!state.dockerfileContent) return null;

  const handleDownload = () => {
    const blob = new Blob([content], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'Dockerfile';
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
    dispatch({
      type: 'SHOW_ALERT',
      payload: { message: 'Dockerfile이 다운로드되었습니다!', type: 'success', isHtml: false },
    });
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      dispatch({
        type: 'SHOW_ALERT',
        payload: { message: '클립보드에 복사되었습니다!', type: 'success', isHtml: false },
      });
    } catch (err: any) {
      dispatch({
        type: 'SHOW_ALERT',
        payload: { message: '복사 실패: ' + err.message, type: 'error', isHtml: false },
      });
    }
  };

  const handleReset = () => {
    setShowResetModal(false);
    dispatch({ type: 'RESET' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <>
      <div className="mt-8">
        <h2 className="text-2xl font-semibold mb-4">4. Dockerfile 미리보기</h2>
        <div className="mb-4 border border-gray-300 rounded-md overflow-hidden">
          <CodeMirror
            value={state.dockerfileContent}
            height="400px"
            theme={oneDark}
            extensions={[StreamLanguage.define(dockerFile)]}
            onChange={handleChange}
          />
        </div>
        <div className="flex gap-4">
          <button
            onClick={handleDownload}
            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition"
          >
            다운로드
          </button>
          <button
            onClick={handleCopy}
            className="px-6 py-2 bg-gray-600 text-white rounded-md hover:bg-gray-700 transition"
          >
            클립보드에 복사
          </button>
          <button
            onClick={() => setShowResetModal(true)}
            className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition flex items-center gap-2"
          >
            <span>🔄</span>
            <span>설정 초기화</span>
          </button>
        </div>
      </div>
      <ResetConfirmModal
        isOpen={showResetModal}
        onCancel={() => setShowResetModal(false)}
        onConfirm={handleReset}
      />
    </>
  );
}

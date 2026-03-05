import { useState, useCallback } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { StreamLanguage } from '@codemirror/language';
import { groovy } from '@codemirror/legacy-modes/mode/groovy';
import { oneDark } from '@codemirror/theme-one-dark';

interface Props {
  isOpen: boolean;
  pipelineScript: string;
  onClose: () => void;
  onBuild: (editedScript: string) => void;
}

export default function PipelinePreviewModal({ isOpen, pipelineScript, onClose, onBuild }: Props) {
  const [editedScript, setEditedScript] = useState('');

  const handleChange = useCallback((value: string) => {
    setEditedScript(value);
  }, []);

  if (!isOpen) return null;

  const currentScript = editedScript || pipelineScript;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-5xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="p-6 border-b border-gray-200 flex items-center justify-between">
          <div>
            <h3 className="text-2xl font-semibold text-gray-800">Jenkins Image Build Pipeline</h3>
            <p className="text-sm text-gray-500 mt-1">파이프라인 스크립트를 확인하고 수정할 수 있습니다</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-auto p-6">
          <div className="border border-gray-300 rounded-md overflow-hidden">
            <CodeMirror
              value={pipelineScript}
              height="500px"
              theme={oneDark}
              extensions={[StreamLanguage.define(groovy)]}
              onChange={handleChange}
            />
          </div>
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-gray-200 flex justify-between items-center">
          <div className="text-sm text-gray-500">
            <span className="font-medium">💡 Tip:</span> 스크립트를 수정한 후 빌드하면 수정된 내용이 적용됩니다
          </div>
          <div className="flex gap-3">
            <button onClick={onClose}
              className="px-6 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 transition">
              닫기
            </button>
            <button onClick={() => onBuild(currentScript)}
              className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition flex items-center gap-2">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              <span>이 스크립트로 빌드하기</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

import { useRef } from 'react';
import { useApp } from '../context/AppContext';
import { uploadJavaArtifact } from '../api/client';

interface Props {
  onNext: () => void;
}

export default function FileUpload({ onNext }: Props) {
  const { state, dispatch } = useApp();
  const fileInputRef = useRef<HTMLInputElement>(null);

  if (state.currentLanguage !== 'java') return null;

  const handleUploadAndNext = async () => {
    const files = fileInputRef.current?.files;
    if (!files || files.length === 0) {
      dispatch({
        type: 'SHOW_ALERT',
        payload: { message: 'JAR 파일을 업로드해주세요.', type: 'error', isHtml: false },
      });
      return;
    }

    dispatch({ type: 'SET_LOADING', payload: true });
    try {
      const data = await uploadJavaArtifact(files[0]);
      dispatch({ type: 'SET_SESSION_ID', payload: data.session_id });
      dispatch({ type: 'SET_JAR_FILENAME', payload: files[0].name });
      dispatch({
        type: 'SHOW_ALERT',
        payload: { message: 'Jar 파일 확인 완료', type: 'success', isHtml: false },
      });
      onNext();
    } catch (error: any) {
      const msg = error.response?.data?.detail || error.message || 'Upload failed';
      dispatch({
        type: 'SHOW_ALERT',
        payload: { message: '파일 업로드 실패: ' + msg, type: 'error', isHtml: false },
      });
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  };

  return (
    <div className="mt-8">
      <h2 className="text-2xl font-semibold mb-4">2. 빌드 Artifact 설정</h2>
      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          JAR 파일 업로드
        </label>
        <input
          ref={fileInputRef}
          type="file"
          accept=".jar,.war"
          className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
        />
        <p className="mt-1 text-sm text-gray-500">빌드된 JAR 파일을 업로드하세요.</p>
      </div>
      <button
        onClick={handleUploadAndNext}
        className="mt-4 px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition"
      >
        다음 단계
      </button>
    </div>
  );
}

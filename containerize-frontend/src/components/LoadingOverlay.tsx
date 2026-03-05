import { useApp } from '../context/AppContext';

export default function LoadingOverlay() {
  const { state } = useApp();

  if (!state.isLoading) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white p-6 rounded-lg shadow-xl">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto" />
        <p className="mt-4 text-gray-700">처리 중...</p>
      </div>
    </div>
  );
}

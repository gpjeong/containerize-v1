interface Props {
  isOpen: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export default function ResetConfirmModal({ isOpen, onCancel, onConfirm }: Props) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        <div className="p-6">
          <div className="flex items-center justify-center mb-4">
            <div className="text-5xl">🔄</div>
          </div>
          <h3 className="text-xl font-semibold text-gray-800 text-center mb-2">
            설정 초기화
          </h3>
          <p className="text-gray-600 text-center mb-6">
            모든 설정을 초기화하시겠습니까?
          </p>
          <div className="flex gap-3">
            <button
              onClick={onCancel}
              className="flex-1 px-4 py-2 bg-gray-200 text-gray-700 rounded-md hover:bg-gray-300 transition"
            >
              취소
            </button>
            <button
              onClick={onConfirm}
              className="flex-1 px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition"
            >
              확인
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

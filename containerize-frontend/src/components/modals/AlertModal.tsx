import { useApp } from '../../context/AppContext';

export default function AlertModal() {
  const { state, dispatch } = useApp();
  const { alert } = state;

  if (!alert.isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        <div className="p-6">
          {alert.type === 'error' && (
            <>
              <div className="flex items-center justify-center mb-4">
                <div className="text-5xl">⚠️</div>
              </div>
              <h3 className="text-xl font-semibold text-gray-800 text-center mb-2">
                입력 오류
              </h3>
            </>
          )}
          {alert.isHtml ? (
            <div
              className="text-gray-600 text-center mb-6"
              dangerouslySetInnerHTML={{ __html: alert.message }}
            />
          ) : (
            <p className="text-gray-600 text-center mb-6">{alert.message}</p>
          )}
          <div className="flex justify-center">
            <button
              onClick={() => dispatch({ type: 'CLOSE_ALERT' })}
              className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition"
            >
              확인
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

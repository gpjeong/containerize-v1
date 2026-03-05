import { useApp } from '../context/AppContext';
import type { Language } from '../types';

const languages: { id: Language; name: string; icon: string; description: string; borderColor: string; bgColor: string }[] = [
  { id: 'python', name: 'Python', icon: '/icons/python.svg', description: 'Flask, FastAPI, Django', borderColor: 'border-blue-500', bgColor: 'bg-blue-50' },
  { id: 'nodejs', name: 'Node.js', icon: '/icons/nodejs.svg', description: 'Express, NestJS', borderColor: 'border-green-500', bgColor: 'bg-green-50' },
  { id: 'java', name: 'Java', icon: '/icons/java.svg', description: 'Spring Boot', borderColor: 'border-red-500', bgColor: 'bg-red-50' },
];

export default function LanguageSelector() {
  const { state, dispatch } = useApp();

  const handleSelect = (lang: Language) => {
    dispatch({ type: 'SET_LANGUAGE', payload: lang });
  };

  return (
    <div>
      <h2 className="text-2xl font-semibold mb-4">1. 개발 언어 선택</h2>
      <div className="grid grid-cols-3 gap-4">
        {languages.map((lang) => {
          const isSelected = state.currentLanguage === lang.id;
          return (
            <button
              key={lang.id}
              onClick={() => handleSelect(lang.id)}
              className={`p-6 border-2 rounded-lg transition ${
                isSelected
                  ? `${lang.borderColor} ${lang.bgColor}`
                  : 'border-gray-300 hover:border-gray-400 hover:bg-gray-50'
              }`}
            >
              <div className="flex justify-center items-center mb-2">
                <img src={lang.icon} alt={lang.name} className="w-16 h-16" />
              </div>
              <div className="font-semibold">{lang.name}</div>
              <div className="text-sm text-gray-500">{lang.description}</div>
            </button>
          );
        })}
      </div>
    </div>
  );
}

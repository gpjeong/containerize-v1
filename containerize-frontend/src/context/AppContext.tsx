import { createContext, useContext, useReducer, type ReactNode } from 'react';
import type { Language, AlertState } from '../types';

interface AppState {
  currentLanguage: Language | null;
  currentSessionId: string | null;
  currentJarFileName: string | null;
  dockerfileContent: string;
  isLoading: boolean;
  alert: AlertState;
}

type AppAction =
  | { type: 'SET_LANGUAGE'; payload: Language }
  | { type: 'SET_SESSION_ID'; payload: string }
  | { type: 'SET_JAR_FILENAME'; payload: string }
  | { type: 'SET_DOCKERFILE'; payload: string }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SHOW_ALERT'; payload: Omit<AlertState, 'isOpen'> }
  | { type: 'CLOSE_ALERT' }
  | { type: 'RESET' };

const initialState: AppState = {
  currentLanguage: null,
  currentSessionId: null,
  currentJarFileName: null,
  dockerfileContent: '',
  isLoading: false,
  alert: { isOpen: false, message: '', type: 'error', isHtml: false },
};

function appReducer(state: AppState, action: AppAction): AppState {
  switch (action.type) {
    case 'SET_LANGUAGE':
      return {
        ...initialState,
        currentLanguage: action.payload,
        alert: state.alert,
      };
    case 'SET_SESSION_ID':
      return { ...state, currentSessionId: action.payload };
    case 'SET_JAR_FILENAME':
      return { ...state, currentJarFileName: action.payload };
    case 'SET_DOCKERFILE':
      return { ...state, dockerfileContent: action.payload };
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    case 'SHOW_ALERT':
      return {
        ...state,
        alert: { ...action.payload, isOpen: true },
      };
    case 'CLOSE_ALERT':
      return {
        ...state,
        alert: { ...state.alert, isOpen: false },
      };
    case 'RESET':
      return initialState;
    default:
      return state;
  }
}

const AppContext = createContext<{
  state: AppState;
  dispatch: React.Dispatch<AppAction>;
} | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(appReducer, initialState);
  return (
    <AppContext.Provider value={{ state, dispatch }}>
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
}

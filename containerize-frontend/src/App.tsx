import { useState } from 'react';
import { useApp } from './context/AppContext';
import Header from './components/Header';
import Footer from './components/Footer';
import LanguageSelector from './components/LanguageSelector';
import FileUpload from './components/FileUpload';
import ConfigForm from './components/ConfigForm';
import DockerfilePreview from './components/DockerfilePreview';
import JenkinsBuild from './components/JenkinsBuild';
import LoadingOverlay from './components/LoadingOverlay';
import AlertModal from './components/modals/AlertModal';

export default function App() {
  const { state } = useApp();
  const [showConfig, setShowConfig] = useState(false);

  const isJava = state.currentLanguage === 'java';
  const showConfigForm =
    state.currentLanguage === 'python' ||
    state.currentLanguage === 'nodejs' ||
    (isJava && showConfig);

  return (
    <div className="container mx-auto px-4 py-8 max-w-6xl">
      <Header />

      <div className="bg-white rounded-lg shadow-lg p-6 mb-6">
        {/* Step 1: Language Selection */}
        <LanguageSelector />

        {/* Step 2: File Upload (Java only) */}
        {isJava && !showConfig && (
          <FileUpload onNext={() => setShowConfig(true)} />
        )}

        {/* Step 2/3: Configuration */}
        {showConfigForm && <ConfigForm />}

        {/* Step 4: Dockerfile Preview */}
        <DockerfilePreview />

        {/* Step 5: Jenkins Build */}
        <JenkinsBuild />
      </div>

      <Footer />

      {/* Global Overlays */}
      <LoadingOverlay />
      <AlertModal />
    </div>
  );
}

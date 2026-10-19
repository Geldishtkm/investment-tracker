import React, { useState } from 'react';
import { Eye, EyeOff, Lock, User, ArrowRight, Sparkles, Shield, Zap, Smartphone } from 'lucide-react';
import { LoginCredentials, MfaSetupResponse } from '../types';
import { authService, AuthError } from '../services/authService';
import VerifyEmailPage from './VerifyEmailPage';

interface LoginPageProps {
  onLoginSuccess: () => void;
  onSwitchToRegister: () => void;
}

const LoginPage: React.FC<LoginPageProps> = ({ onLoginSuccess, onSwitchToRegister }) => {
  const [credentials, setCredentials] = useState<LoginCredentials>({
    username: '',
    password: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);
  const [verifiedNotice, setVerifiedNotice] = useState('');
  const [requiresMfa, setRequiresMfa] = useState(false);
  const [mfaCode, setMfaCode] = useState('');
  const [mfaUsername, setMfaUsername] = useState('');
  const [showMfaSetup, setShowMfaSetup] = useState(false);
  const [mfaSetup, setMfaSetup] = useState<MfaSetupResponse | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      const authResponse = await authService.login(credentials);
      
      // Check if MFA is required
      if (authResponse.requiresMfa) {
        setRequiresMfa(true);
        setMfaUsername(credentials.username);
        return;
      }
      
      // Token is already saved by authService.login(), no need to call saveToken again
      
      // Show MFA setup option after successful login
      setShowMfaSetup(true);
      
      onLoginSuccess();
    } catch (error) {
      if (error instanceof AuthError && error.code === 'EMAIL_NOT_VERIFIED' && error.email) {
        // Account exists but was never verified: go straight to the code screen
        setUnverifiedEmail(error.email);
        return;
      }
      setError(error instanceof Error ? error.message : 'Login failed');
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (field: keyof LoginCredentials) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setCredentials(prev => ({
      ...prev,
      [field]: e.target.value
    }));
    if (error) setError(''); // Clear error when user starts typing
  };

  const handleMfaVerification = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!mfaCode.trim()) {
      setError('Please enter your MFA code');
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const mfaResponse = await authService.verifyMfa(mfaUsername, mfaCode);
      
      if (mfaResponse.success && mfaResponse.token) {
        // Save the token and complete login
        authService.saveToken(mfaResponse.token);
        onLoginSuccess();
      } else {
        setError(mfaResponse.message || 'MFA verification failed');
      }
    } catch (error) {
      setError(error instanceof Error ? error.message : 'MFA verification failed');
    } finally {
      setIsLoading(false);
    }
  };

  if (unverifiedEmail) {
    return (
      <VerifyEmailPage
        email={unverifiedEmail}
        initialCooldownSeconds={0}
        initialNotice="Your email isn't verified yet. Enter the code we sent you, or request a new one."
        onVerified={() => {
          setUnverifiedEmail(null);
          setVerifiedNotice('Email verified! Log in to continue.');
        }}
        onBack={() => setUnverifiedEmail(null)}
      />
    );
  }

  return (
    <div className="min-h-screen relative overflow-hidden bg-term-bg">
      {/* Animated Background */}
      <div className="hidden"></div>
      <div className="hidden"></div>
      
      <div className="relative z-10 flex items-center justify-center min-h-screen p-6">
        <div className="w-full max-w-md">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="w-20 h-20 rounded-lg flex items-center justify-center mx-auto mb-6 bg-term-raised border border-term-line2">
              <Shield size={32} className="text-term-text" />
            </div>
            <h1 className="text-2xl font-bold mb-2 text-term-text">
              Welcome Back
            </h1>
            <p className="text-term-muted text-lg">
              Sign in to access your portfolio
            </p>
          </div>

          {/* Login Form */}
          <div className="glass-card p-6 border border-term-line2">
            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Username Field */}
              <div className="space-y-2">
                <label className="block text-sm font-medium text-term-muted">
                  Username
                </label>
                <div className="relative">
                  <div className="absolute left-4 top-1/2 -translate-y-1/2">
                    <User size={20} className="text-term-accent" />
                  </div>
                  <input
                    type="text"
                    value={credentials.username}
                    onChange={handleInputChange('username')}
                    className="w-full pl-12 pr-4 py-2.5 border border-term-line2 rounded-lg text-term-text placeholder:text-term-dim focus:outline-none focus:border-term-accent transition-all bg-term-raised"
                    placeholder="Enter your username"
                    required
                    disabled={isLoading}
                  />
                </div>
              </div>

              {/* Password Field */}
              <div className="space-y-2">
                <label className="block text-sm font-medium text-term-muted">
                  Password
                </label>
                <div className="relative">
                  <div className="absolute left-4 top-1/2 -translate-y-1/2">
                    <Lock size={20} className="text-term-accent" />
                  </div>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={credentials.password}
                    onChange={handleInputChange('password')}
                    className="w-full pl-12 pr-12 py-2.5 border border-term-line2 rounded-lg text-term-text placeholder:text-term-dim focus:outline-none focus:border-term-accent transition-all bg-term-raised"
                    placeholder="Enter your password"
                    required
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-term-muted hover:text-term-text transition-colors"
                    disabled={isLoading}
                  >
                    {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                </div>
              </div>

              {/* Success Message after verifying an email */}
              {verifiedNotice && !error && (
                <div className="p-4 border border-term-line2 rounded-md bg-term-raised">
                  <div className="flex items-center gap-2 text-term-accent">
                    <div className="w-2 h-2 bg-term-raised rounded-full"></div>
                    <span className="text-sm font-medium">{verifiedNotice}</span>
                  </div>
                </div>
              )}

              {/* Error Message */}
              {error && (
                <div className="p-4 border border-term-down/50 rounded-md bg-term-raised border-term-line2">
                  <div className="flex items-center gap-2 text-term-down">
                    <div className="w-2 h-2 bg-term-down-soft rounded-full"></div>
                    <span className="text-sm font-medium">{error}</span>
                  </div>
                </div>
              )}

              {/* Login Button */}
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-2.5 disabled:from-gray-600 disabled:to-gray-700 text-term-text font-semibold rounded-lg transition-all border border-term-line2 disabled:transform-none disabled:cursor-not-allowed flex items-center justify-center gap-2 bg-term-accent text-term-bg hover:bg-[#959BFF]"
              >
                {isLoading ? (
                  <>
                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                    Signing In...
                  </>
                ) : (
                  <>
                    <span>Sign In</span>
                    <ArrowRight size={20} />
                  </>
                )}
              </button>

              {/* Switch to Register */}
              <div className="text-center">
                <p className="text-term-muted text-sm">
                  Don't have an account?{' '}
                  <button
                    type="button"
                    onClick={onSwitchToRegister}
                    className="text-term-accent hover:text-term-accent font-medium transition-colors"
                    disabled={isLoading}
                  >
                    Create one here
                  </button>
                </p>
              </div>
            </form>
          </div>

          {/* MFA Verification Section */}
          {requiresMfa && (
            <div className="mt-8 glass-card p-6 border border-term-line2">
              <div className="text-center mb-6">
                <div className="w-16 h-16 rounded-lg flex items-center justify-center mx-auto mb-4 bg-term-raised border border-term-line2">
                  <Smartphone size={24} className="text-term-text" />
                </div>
                <h3 className="text-2xl font-bold mb-2 text-term-text">
                  🔐 Multi-Factor Authentication
                </h3>
                <p className="text-term-muted">
                  Enter the 6-digit code from your authenticator app
                </p>
              </div>

              <form onSubmit={handleMfaVerification} className="space-y-4">
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-term-muted">
                    MFA Code
                  </label>
                  <input
                    type="text"
                    value={mfaCode}
                    onChange={(e) => setMfaCode(e.target.value)}
                    className="w-full px-4 py-3 border border-term-line2 rounded-md text-term-text placeholder:text-term-dim focus:outline-none focus:border-term-accent transition-all text-center text-lg font-mono tracking-widest bg-term-raised"
                    placeholder="000000"
                    maxLength={6}
                    required
                    disabled={isLoading}
                  />
                </div>

                {error && (
                  <div className="p-4 border border-term-down/50 rounded-md bg-term-raised border-term-line2">
                    <div className="flex items-center gap-2 text-term-down">
                      <div className="w-2 h-2 bg-term-down-soft rounded-full"></div>
                      <span className="text-sm font-medium">{error}</span>
                    </div>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full py-3 disabled:from-gray-600 disabled:to-gray-700 text-term-text font-semibold rounded-md transition-all border border-term-line2 disabled:transform-none disabled:cursor-not-allowed flex items-center justify-center gap-2 bg-term-accent text-term-bg hover:bg-[#959BFF]"
                >
                  {isLoading ? (
                    <>
                      <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                      Verifying...
                    </>
                  ) : (
                    <>
                      <span>Verify & Sign In</span>
                      <ArrowRight size={18} />
                    </>
                  )}
                </button>

                <div className="text-center">
                  <button
                    type="button"
                    onClick={() => {
                      setRequiresMfa(false);
                      setMfaCode('');
                      setError('');
                    }}
                    className="text-term-accent hover:text-term-accent text-sm transition-colors"
                    disabled={isLoading}
                  >
                    ← Back to Login
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* MFA Setup Section - Only shown after successful login */}
          {showMfaSetup && (
            <div className="mt-8 glass-card p-6 border border-term-line2">
              <div className="text-center mb-6">
                <div className="w-16 h-16 rounded-lg flex items-center justify-center mx-auto mb-4 bg-term-raised border border-term-line2">
                  <Shield size={24} className="text-term-text" />
                </div>
                <h3 className="text-2xl font-bold mb-2 text-term-text">
                  🔐 Setup Multi-Factor Authentication
                </h3>
                <p className="text-term-muted">
                  Enhance your account security with MFA (optional)
                </p>
              </div>

              <div className="space-y-4">
                <button
                  onClick={async () => {
                    try {
                      const response = await authService.setupMfa(credentials.username, credentials.password);
                      if (response.success) {
                        setMfaSetup(response);
                      }
                    } catch (error) {
                      console.error('MFA setup failed:', error);
                    }
                  }}
                  className="w-full py-3 text-term-text font-semibold rounded-md transition-all bg-term-accent text-term-bg hover:bg-[#959BFF]"
                >
                  Setup MFA
                </button>
                
                <button
                  onClick={() => setShowMfaSetup(false)}
                  className="w-full py-3 bg-term-raised hover:bg-term-hover text-term-text font-semibold rounded-md transition-all"
                >
                  Skip for Now
                </button>
              </div>

              {/* MFA Setup Result */}
              {mfaSetup && (
                <div className="mt-6 p-4 border border-term-line2 rounded-md bg-term-raised">
                  <div className="text-center">
                    <h4 className="text-term-text font-semibold mb-2">MFA Setup Complete! 🎉</h4>
                    <p className="text-term-muted text-sm mb-4">
                      Your account is now protected with multi-factor authentication.
                    </p>
                    <button
                      onClick={() => setShowMfaSetup(false)}
                      className="px-6 py-2 bg-term-raised hover:bg-term-hover text-term-text font-semibold rounded-lg transition-colors"
                    >
                      Continue
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Features */}
          <div className="mt-8 grid grid-cols-1 gap-4">
            <div className="glass-card p-4 border border-term-line2">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-md flex items-center justify-center bg-term-raised border border-term-line2">
                  <Zap size={20} className="text-term-text" />
                </div>
                <div>
                  <h3 className="text-term-text font-semibold">Secure Access</h3>
                  <p className="text-term-muted text-sm">Your data is protected with JWT authentication</p>
                </div>
              </div>
            </div>
            
            <div className="glass-card p-4 border border-term-line2">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-md flex items-center justify-center bg-term-raised border border-term-line2">
                  <Sparkles size={20} className="text-term-text" />
                </div>
                <div>
                  <h3 className="text-term-text font-semibold">Real-time Data</h3>
                  <p className="text-term-muted text-sm">Live cryptocurrency prices and portfolio tracking</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage; 
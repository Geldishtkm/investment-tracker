import React, { useState } from 'react';
import { Eye, EyeOff, Lock, User, ArrowRight, Sparkles, Shield, Zap, Smartphone } from 'lucide-react';
import { LoginCredentials, MfaVerificationResponse } from '../types';
import { authService } from '../services/authService';

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
  const [requiresMfa, setRequiresMfa] = useState(false);
  const [mfaCode, setMfaCode] = useState('');
  const [mfaUsername, setMfaUsername] = useState('');

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
        console.log('MFA required for user:', credentials.username);
        return;
      }
      
      // Token is already saved by authService.login(), no need to call saveToken again
      console.log('Login successful for user:', authResponse.username);
      onLoginSuccess();
    } catch (error) {
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
        console.log('MFA verification successful, login completed');
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

  return (
    <div className="min-h-screen bg-gradient-to-br from-black via-blue-950 to-black relative overflow-hidden">
      {/* Animated Background */}
      <div className="absolute inset-0 bg-gradient-to-r from-blue-600/20 via-indigo-600/20 to-purple-600/20 animate-pulse"></div>
      <div className="absolute top-0 left-0 w-full h-full bg-[radial-gradient(circle_at_50%_50%,rgba(59,130,246,0.3),transparent_50%)]"></div>
      
      <div className="relative z-10 flex items-center justify-center min-h-screen p-6">
        <div className="w-full max-w-md">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="w-20 h-20 bg-gradient-to-br from-blue-600 to-indigo-600 rounded-3xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-600/40">
              <Shield size={32} className="text-white" />
            </div>
            <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-400 via-indigo-400 to-purple-400 bg-clip-text text-transparent mb-2">
              Welcome Back
            </h1>
            <p className="text-gray-400 text-lg">
              Sign in to access your portfolio
            </p>
          </div>

          {/* Login Form */}
          <div className="glass-card p-8 border border-blue-600/30">
            <form onSubmit={handleSubmit} className="space-y-6">
              {/* Username Field */}
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-300">
                  Username
                </label>
                <div className="relative">
                  <div className="absolute left-4 top-1/2 transform -translate-y-1/2">
                    <User size={20} className="text-blue-400" />
                  </div>
                  <input
                    type="text"
                    value={credentials.username}
                    onChange={handleInputChange('username')}
                    className="w-full pl-12 pr-4 py-4 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-blue-600/40 rounded-2xl text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all duration-300"
                    placeholder="Enter your username"
                    required
                    disabled={isLoading}
                  />
                </div>
              </div>

              {/* Password Field */}
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-300">
                  Password
                </label>
                <div className="relative">
                  <div className="absolute left-4 top-1/2 transform -translate-y-1/2">
                    <Lock size={20} className="text-blue-400" />
                  </div>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={credentials.password}
                    onChange={handleInputChange('password')}
                    className="w-full pl-12 pr-12 py-4 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-blue-600/40 rounded-2xl text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all duration-300"
                    placeholder="Enter your password"
                    required
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-4 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-white transition-colors"
                    disabled={isLoading}
                  >
                    {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                </div>
              </div>

              {/* Error Message */}
              {error && (
                <div className="p-4 bg-gradient-to-r from-red-600/20 to-red-500/20 backdrop-blur-sm border border-red-500/40 rounded-xl">
                  <div className="flex items-center gap-2 text-red-400">
                    <div className="w-2 h-2 bg-red-400 rounded-full"></div>
                    <span className="text-sm font-medium">{error}</span>
                  </div>
                </div>
              )}

              {/* Login Button */}
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-4 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 disabled:from-gray-600 disabled:to-gray-700 text-white font-semibold rounded-2xl transition-all duration-300 transform hover:scale-105 hover:shadow-lg shadow-md border border-blue-500/40 disabled:transform-none disabled:cursor-not-allowed flex items-center justify-center gap-2"
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
                <p className="text-gray-400 text-sm">
                  Don't have an account?{' '}
                  <button
                    type="button"
                    onClick={onSwitchToRegister}
                    className="text-blue-400 hover:text-blue-300 font-medium transition-colors"
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
            <div className="mt-8 glass-card p-6 border border-purple-600/30">
              <div className="text-center mb-6">
                <div className="w-16 h-16 bg-gradient-to-br from-purple-600 to-pink-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-purple-600/40">
                  <Smartphone size={24} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold bg-gradient-to-r from-purple-400 to-pink-400 bg-clip-text text-transparent mb-2">
                  🔐 Multi-Factor Authentication
                </h3>
                <p className="text-gray-400">
                  Enter the 6-digit code from your authenticator app
                </p>
              </div>

              <form onSubmit={handleMfaVerification} className="space-y-4">
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-gray-300">
                    MFA Code
                  </label>
                  <input
                    type="text"
                    value={mfaCode}
                    onChange={(e) => setMfaCode(e.target.value)}
                    className="w-full px-4 py-3 bg-gradient-to-r from-gray-900/80 to-gray-800/80 backdrop-blur-sm border border-purple-600/40 rounded-xl text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all duration-300 text-center text-lg font-mono tracking-widest"
                    placeholder="000000"
                    maxLength={6}
                    required
                    disabled={isLoading}
                  />
                </div>

                {error && (
                  <div className="p-4 bg-gradient-to-r from-red-600/20 to-red-500/20 backdrop-blur-sm border border-red-500/40 rounded-xl">
                    <div className="flex items-center gap-2 text-red-400">
                      <div className="w-2 h-2 bg-red-400 rounded-full"></div>
                      <span className="text-sm font-medium">{error}</span>
                    </div>
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full py-3 bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700 disabled:from-gray-600 disabled:to-gray-700 text-white font-semibold rounded-xl transition-all duration-300 transform hover:scale-105 hover:shadow-lg shadow-md border border-purple-500/40 disabled:transform-none disabled:cursor-not-allowed flex items-center justify-center gap-2"
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
                    className="text-purple-400 hover:text-purple-300 text-sm transition-colors"
                    disabled={isLoading}
                  >
                    ← Back to Login
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Features */}
          <div className="mt-8 grid grid-cols-1 gap-4">
            <div className="glass-card p-4 border border-blue-600/20">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-gradient-to-br from-green-600 to-emerald-600 rounded-xl flex items-center justify-center">
                  <Zap size={20} className="text-white" />
                </div>
                <div>
                  <h3 className="text-white font-semibold">Secure Access</h3>
                  <p className="text-gray-400 text-sm">Your data is protected with JWT authentication</p>
                </div>
              </div>
            </div>
            
            <div className="glass-card p-4 border border-blue-600/20">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-gradient-to-br from-purple-600 to-pink-600 rounded-xl flex items-center justify-center">
                  <Sparkles size={20} className="text-white" />
                </div>
                <div>
                  <h3 className="text-white font-semibold">Real-time Data</h3>
                  <p className="text-gray-400 text-sm">Live cryptocurrency prices and portfolio tracking</p>
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
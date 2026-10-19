import React, { useState } from 'react';
import { Eye, EyeOff, Lock, User, Mail, ArrowRight, Sparkles, UserPlus, CheckCircle } from 'lucide-react';
import { RegisterCredentials } from '../types';
import { authService } from '../services/authService';
import VerifyEmailPage from './VerifyEmailPage';

interface RegisterPageProps {
  onRegisterSuccess: () => void;
  onSwitchToLogin: () => void;
}

const RegisterPage: React.FC<RegisterPageProps> = ({ onRegisterSuccess, onSwitchToLogin }) => {
  const [credentials, setCredentials] = useState<RegisterCredentials>({
    username: '',
    email: '',
    password: ''
  });
  // Set after signup: the account exists but must be verified with the emailed code
  const [pendingVerification, setPendingVerification] = useState<{
    email: string;
    cooldown: number;
    notice?: string;
  } | null>(null);
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [registrationSuccess, setRegistrationSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');
    setRegistrationSuccess(false); // Reset success state on new submission

    // Validate passwords match
    if (credentials.password !== confirmPassword) {
      setError('Passwords do not match');
      setIsLoading(false);
      return;
    }

    // Validate password strength
    if (credentials.password.length < 6) {
      setError('Password must be at least 6 characters long');
      setIsLoading(false);
      return;
    }

    try {
      // Create the account; it stays inactive until the emailed code is verified
      const result = await authService.register(credentials);
      setRegistrationSuccess(true);
      setPendingVerification({
        email: result.email,
        cooldown: result.resendCooldownSeconds,
        // Explain a resumed signup or a failed send; a normal first send needs no extra notice
        notice: result.resumed || !result.emailSent ? result.message : undefined
      });
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Registration failed');
    } finally {
      setIsLoading(false);
    }
  };

  const handleInputChange = (field: keyof RegisterCredentials) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setCredentials(prev => ({
      ...prev,
      [field]: e.target.value
    }));
    if (error) setError(''); // Clear error when user starts typing
  };

  const handleConfirmPasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setConfirmPassword(e.target.value);
    if (error) setError(''); // Clear error when user starts typing
  };

  if (pendingVerification) {
    return (
      <VerifyEmailPage
        email={pendingVerification.email}
        initialCooldownSeconds={pendingVerification.cooldown}
        initialNotice={pendingVerification.notice}
        onVerified={() => onRegisterSuccess()}
        onBack={onSwitchToLogin}
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
              <UserPlus size={32} className="text-term-text" />
            </div>
            <h1 className="text-2xl font-bold mb-2 text-term-text">
              Create Account
            </h1>
            <p className="text-term-muted text-lg">
              Join us and start tracking your portfolio
            </p>
          </div>

          {/* Register Form */}
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
                    placeholder="Choose a username"
                    required
                    disabled={isLoading}
                  />
                </div>
              </div>

              {/* Email Field */}
              <div className="space-y-2">
                <label className="block text-sm font-medium text-term-muted">
                  Email
                </label>
                <div className="relative">
                  <div className="absolute left-4 top-1/2 -translate-y-1/2">
                    <Mail size={20} className="text-term-accent" />
                  </div>
                  <input
                    type="email"
                    value={credentials.email}
                    onChange={handleInputChange('email')}
                    className="w-full pl-12 pr-4 py-2.5 border border-term-line2 rounded-lg text-term-text placeholder:text-term-dim focus:outline-none focus:border-term-accent transition-all bg-term-raised"
                    placeholder="you@example.com"
                    autoComplete="email"
                    required
                    disabled={isLoading}
                  />
                </div>
                <p className="text-xs text-term-dim">
                  We'll send a 6-digit code to verify it
                </p>
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
                    placeholder="Create a password"
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
                <p className="text-xs text-term-dim">
                  Password must be at least 6 characters long
                </p>
              </div>

              {/* Confirm Password Field */}
              <div className="space-y-2">
                <label className="block text-sm font-medium text-term-muted">
                  Confirm Password
                </label>
                <div className="relative">
                  <div className="absolute left-4 top-1/2 -translate-y-1/2">
                    <Lock size={20} className="text-term-accent" />
                  </div>
                  <input
                    type={showConfirmPassword ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={handleConfirmPasswordChange}
                    className="w-full pl-12 pr-12 py-2.5 border border-term-line2 rounded-lg text-term-text placeholder:text-term-dim focus:outline-none focus:border-term-accent transition-all bg-term-raised"
                    placeholder="Confirm your password"
                    required
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-term-muted hover:text-term-text transition-colors"
                    disabled={isLoading}
                  >
                    {showConfirmPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                  </button>
                </div>
              </div>

              {/* Error Message */}
              {error && (
                <div className="p-4 border border-term-down/50 rounded-md bg-term-raised border-term-line2">
                  <div className="flex items-center gap-2 text-term-down">
                    <div className="w-2 h-2 bg-term-down-soft rounded-full"></div>
                    <span className="text-sm font-medium">{error}</span>
                  </div>
                </div>
              )}

              {/* Register Button */}
              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-2.5 disabled:from-gray-600 disabled:to-gray-700 text-term-text font-semibold rounded-lg transition-all border border-term-line2 disabled:transform-none disabled:cursor-not-allowed flex items-center justify-center gap-2 bg-term-accent text-term-bg hover:bg-[#7AA3F3]"
              >
                {isLoading ? (
                  <>
                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                    Creating Account...
                  </>
                ) : (
                  <>
                    <span>Create Account</span>
                    <ArrowRight size={20} />
                  </>
                )}
              </button>

              {/* Switch to Login */}
              <div className="text-center">
                <p className="text-term-muted text-sm">
                  Already have an account?{' '}
                  <button
                    type="button"
                    onClick={onSwitchToLogin}
                    className="text-term-accent hover:text-term-accent font-medium transition-colors"
                    disabled={isLoading}
                  >
                    Sign in here
                  </button>
                </p>
              </div>
            </form>
          </div>

          {/* Success Message */}
          {!isLoading && !error && registrationSuccess && (
            <div className="mt-6 p-4 border border-term-line2 rounded-md bg-term-raised">
              <div className="flex items-center gap-3">
                <CheckCircle className="w-5 h-5 text-term-accent" />
                <div>
                  <h4 className="text-term-text font-semibold">Registration Successful! 🎉</h4>
                  <p className="text-term-muted text-sm">
                    Your account has been created. Please login to access your portfolio tracker and optionally setup MFA for enhanced security.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Features */}
          <div className="mt-8 grid grid-cols-1 gap-4">
            <div className="glass-card p-4 border border-term-line2">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-md flex items-center justify-center bg-term-raised border border-term-line2">
                  <CheckCircle size={20} className="text-term-text" />
                </div>
                <div>
                  <h3 className="text-term-text font-semibold">Free to Use</h3>
                  <p className="text-term-muted text-sm">No hidden fees, completely free portfolio tracking</p>
                </div>
              </div>
            </div>
            
            <div className="glass-card p-4 border border-term-line2">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-md flex items-center justify-center bg-term-raised border border-term-line2">
                  <Sparkles size={20} className="text-term-text" />
                </div>
                <div>
                  <h3 className="text-term-text font-semibold">Advanced Analytics</h3>
                  <p className="text-term-muted text-sm">ROI, Sharpe ratio, volatility, and more</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage; 
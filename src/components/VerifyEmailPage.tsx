import React, { useEffect, useRef, useState } from 'react';
import { MailCheck, ArrowLeft, RefreshCw, CheckCircle } from 'lucide-react';
import { authService, AuthError } from '../services/authService';

interface VerifyEmailPageProps {
  email: string;
  /** Seconds before the first resend is allowed (0 if the code still needs to be sent). */
  initialCooldownSeconds: number;
  /** Message to show on arrival, e.g. that the first email could not be sent. */
  initialNotice?: string;
  onVerified: (username: string) => void;
  onBack: () => void;
}

const CODE_LENGTH = 6;

const VerifyEmailPage: React.FC<VerifyEmailPageProps> = ({
  email,
  initialCooldownSeconds,
  initialNotice,
  onVerified,
  onBack
}) => {
  const [digits, setDigits] = useState<string[]>(Array(CODE_LENGTH).fill(''));
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(initialNotice || '');
  const [isVerifying, setIsVerifying] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [verified, setVerified] = useState(false);
  const [cooldown, setCooldown] = useState(initialCooldownSeconds);
  const inputs = useRef<Array<HTMLInputElement | null>>([]);

  // Count down the resend cooldown once per second
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown(c => c - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  useEffect(() => {
    inputs.current[0]?.focus();
  }, []);

  const submit = async (code: string) => {
    if (code.length !== CODE_LENGTH || isVerifying) return;
    setIsVerifying(true);
    setError('');
    try {
      const result = await authService.verifyEmail(email, code);
      setVerified(true);
      setTimeout(() => onVerified(result.username), 1200);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verification failed');
      setDigits(Array(CODE_LENGTH).fill(''));
      inputs.current[0]?.focus();
    } finally {
      setIsVerifying(false);
    }
  };

  const setDigitsAndMaybeSubmit = (next: string[]) => {
    setDigits(next);
    if (error) setError('');
    if (next.every(d => d !== '')) submit(next.join(''));
  };

  const handleChange = (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value.replace(/\D/g, '');
    if (!value) {
      const next = [...digits];
      next[index] = '';
      setDigits(next);
      return;
    }
    // Typing or autofilling several digits at once spreads them across the boxes
    const next = [...digits];
    value.slice(0, CODE_LENGTH - index).split('').forEach((d, i) => { next[index + i] = d; });
    const nextIndex = Math.min(index + value.length, CODE_LENGTH - 1);
    inputs.current[nextIndex]?.focus();
    setDigitsAndMaybeSubmit(next);
  };

  const handleKeyDown = (index: number) => (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      inputs.current[index - 1]?.focus();
    } else if (e.key === 'ArrowLeft' && index > 0) {
      inputs.current[index - 1]?.focus();
    } else if (e.key === 'ArrowRight' && index < CODE_LENGTH - 1) {
      inputs.current[index + 1]?.focus();
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, CODE_LENGTH);
    if (!pasted) return;
    e.preventDefault();
    const next = Array(CODE_LENGTH).fill('');
    pasted.split('').forEach((d, i) => { next[i] = d; });
    inputs.current[Math.min(pasted.length, CODE_LENGTH - 1)]?.focus();
    setDigitsAndMaybeSubmit(next);
  };

  const handleResend = async () => {
    if (cooldown > 0 || isResending) return;
    setIsResending(true);
    setError('');
    setNotice('');
    try {
      const result = await authService.resendCode(email);
      setNotice(`A new code has been sent to ${email}.`);
      setDigits(Array(CODE_LENGTH).fill(''));
      setCooldown(result.resendCooldownSeconds);
      inputs.current[0]?.focus();
    } catch (err) {
      if (err instanceof AuthError && err.retryAfterSeconds) {
        setCooldown(err.retryAfterSeconds);
      }
      setError(err instanceof Error ? err.message : 'Could not resend the code');
    } finally {
      setIsResending(false);
    }
  };

  const formatCooldown = (seconds: number) =>
    `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

  return (
    <div className="min-h-screen relative overflow-hidden bg-term-bg">
      <div className="hidden"></div>
      <div className="hidden"></div>

      <div className="relative z-10 flex items-center justify-center min-h-screen p-6">
        <div className="w-full max-w-md">
          <div className="text-center mb-8">
            <div className="w-20 h-20 rounded-lg flex items-center justify-center mx-auto mb-6 bg-term-raised border border-term-line2">
              {verified ? <CheckCircle size={32} className="text-term-text" /> : <MailCheck size={32} className="text-term-text" />}
            </div>
            <h1 className="text-2xl font-bold mb-2 text-term-text">
              {verified ? 'Email Verified' : 'Check Your Email'}
            </h1>
            <p className="text-term-muted text-lg">
              {verified ? 'Taking you to login…' : (
                <>We sent a 6-digit code to <span className="text-term-text font-medium break-all">{email}</span></>
              )}
            </p>
          </div>

          <div className="glass-card p-6 border border-term-line2">
            <form
              onSubmit={(e) => { e.preventDefault(); submit(digits.join('')); }}
              className="space-y-6"
            >
              <div className="flex justify-between gap-2" onPaste={handlePaste}>
                {digits.map((digit, i) => (
                  <input
                    key={i}
                    ref={(el) => { inputs.current[i] = el; }}
                    type="text"
                    inputMode="numeric"
                    autoComplete={i === 0 ? 'one-time-code' : 'off'}
                    aria-label={`Digit ${i + 1}`}
                    maxLength={CODE_LENGTH}
                    value={digit}
                    onChange={handleChange(i)}
                    onKeyDown={handleKeyDown(i)}
                    disabled={isVerifying || verified}
                    className="w-12 h-14 sm:w-14 sm:h-16 text-center text-2xl font-bold border border-term-line2 rounded-md text-term-text focus:outline-none focus:border-term-accent transition-all disabled:opacity-60 bg-term-raised"
                  />
                ))}
              </div>

              {error && (
                <div className="p-4 border border-term-down/50 rounded-lg text-term-down text-sm bg-term-raised border-term-line2">
                  {error}
                </div>
              )}
              {notice && !error && (
                <div className="p-4 border border-term-line2 rounded-lg text-term-accent text-sm bg-term-raised">
                  {notice}
                </div>
              )}

              <button
                type="submit"
                disabled={digits.some(d => d === '') || isVerifying || verified}
                className="w-full py-2.5 text-term-text font-semibold rounded-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed bg-term-accent text-term-bg hover:bg-[#7AA3F3]"
              >
                {isVerifying ? 'Verifying…' : verified ? 'Verified' : 'Verify Email'}
              </button>
            </form>

            <div className="mt-6 text-center text-sm text-term-muted">
              Didn't get the code? Check your spam folder, or{' '}
              <button
                type="button"
                onClick={handleResend}
                disabled={cooldown > 0 || isResending || verified}
                className="inline-flex items-center gap-1 text-term-accent hover:text-term-accent font-medium disabled:text-gray-500 disabled:cursor-not-allowed"
              >
                <RefreshCw size={14} className={isResending ? 'animate-spin' : ''} />
                {cooldown > 0 ? `resend in ${formatCooldown(cooldown)}` : isResending ? 'sending…' : 'resend code'}
              </button>
            </div>

            <button
              type="button"
              onClick={onBack}
              className="mt-6 w-full inline-flex items-center justify-center gap-2 text-term-muted hover:text-term-text text-sm transition-colors"
            >
              <ArrowLeft size={16} /> Back to login
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VerifyEmailPage;

import React, { useState, useEffect } from 'react';
import { Shield, Lock, Eye, AlertTriangle, CheckCircle, XCircle, Clock, User, Activity } from 'lucide-react';

interface AuditLog {
  id: number;
  user: { username: string };
  action: string;
  entityType: string;
  entityId: number | null;
  details: string;
  ipAddress: string;
  userAgent: string;
  sessionId: string;
  severity: 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';
  timestamp: string;
  success: boolean;
  errorMessage: string | null;
}

interface AuditStatistics {
  totalLogs: number;
  failedActions: number;
  securityEvents: number;
  todayLogs: number;
}

const SecurityDashboard: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'mfa' | 'audit' | 'monitoring'>('mfa');
  const [mfaUsername, setMfaUsername] = useState('');
  const [mfaPassword, setMfaPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [mfaStatus, setMfaStatus] = useState<any>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [auditStats, setAuditStats] = useState<AuditStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error' | 'info'; text: string } | null>(null);

  // MFA Functions
  const setupMfa = async () => {
    if (!mfaUsername || !mfaPassword) {
      showMessage('error', 'Username and password are required');
      return;
    }

    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/security/mfa/setup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: mfaUsername, password: mfaPassword })
      });

      const data = await response.json();
      if (data.success) {
        showMessage('success', 'MFA setup successful! Check the console for your secret and QR code data.');
        console.log('MFA Setup Data:', data);
        setMfaUsername('');
        setMfaPassword('');
      } else {
        showMessage('error', data.message || 'MFA setup failed');
      }
    } catch (error) {
      showMessage('error', `Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setLoading(false);
    }
  };

  const verifyMfa = async () => {
    if (!mfaUsername || !mfaCode) {
      showMessage('error', 'Username and MFA code are required');
      return;
    }

    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/security/mfa/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: mfaUsername, code: mfaCode })
      });

      const data = await response.json();
      if (data.success) {
        showMessage('success', 'MFA verification successful!');
        setMfaCode('');
      } else {
        showMessage('error', data.message || 'MFA verification failed');
      }
    } catch (error) {
      showMessage('error', `Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setLoading(false);
    }
  };

  const getMfaStatus = async () => {
    if (!mfaUsername) {
      showMessage('error', 'Username is required');
      return;
    }

    setLoading(true);
    try {
      const response = await fetch(`http://localhost:8080/api/security/mfa/status/${mfaUsername}`);
      const data = await response.json();
      setMfaStatus(data);
    } catch (error) {
      showMessage('error', `Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setLoading(false);
    }
  };

  const generateMfaCode = async () => {
    if (!mfaUsername) {
      showMessage('error', 'Username is required');
      return;
    }

    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/security/mfa/generate-code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: mfaUsername })
      });

      const data = await response.json();
      if (data.success) {
        showMessage('success', `MFA Code: ${data.code}`);
        setMfaCode(data.code);
      } else {
        showMessage('error', data.message || 'Failed to generate MFA code');
      }
    } catch (error) {
      showMessage('error', `Error: ${error instanceof Error ? error.message : 'Unknown error'}`);
    } finally {
      setLoading(false);
    }
  };

  // Audit Functions
  const loadAuditStatistics = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/admin/audit/statistics');
      const data = await response.json();
      if (data.success) {
        setAuditStats(data.statistics);
      }
    } catch (error) {
      console.error('Error loading audit statistics:', error);
    }
  };

  const loadRecentAuditLogs = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/admin/audit/logs?limit=50');
      const data = await response.json();
      if (data.success) {
        setAuditLogs(data.logs);
      }
    } catch (error) {
      console.error('Error loading audit logs:', error);
    }
  };

  const loadFailedActions = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/admin/audit/failed-actions?limit=50');
      const data = await response.json();
      if (data.success) {
        setAuditLogs(data.failedActions);
      }
    } catch (error) {
      console.error('Error loading failed actions:', error);
    }
  };

  const loadSecurityEvents = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/admin/audit/security-events?limit=50');
      const data = await response.json();
      if (data.success) {
        setAuditLogs(data.securityEvents);
      }
    } catch (error) {
      console.error('Error loading security events:', error);
    }
  };

  // Utility Functions
  const showMessage = (type: 'success' | 'error' | 'info', text: string) => {
    setMessage({ type, text });
    setTimeout(() => setMessage(null), 5000);
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return 'text-red-600 bg-red-100';
      case 'ERROR': return 'text-red-500 bg-red-50';
      case 'WARNING': return 'text-yellow-600 bg-yellow-100';
      case 'INFO': return 'text-blue-600 bg-blue-100';
      default: return 'text-gray-600 bg-gray-100';
    }
  };

  const formatTimestamp = (timestamp: string) => {
    return new Date(timestamp).toLocaleString();
  };

  useEffect(() => {
    if (activeTab === 'audit') {
      loadAuditStatistics();
      loadRecentAuditLogs();
    }
  }, [activeTab]);

  return (
    <div className="max-w-7xl mx-auto p-6 space-y-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-3xl font-bold bg-gradient-to-r from-purple-600 to-blue-600 bg-clip-text text-transparent mb-2">
          🔐 Security & Compliance Dashboard
        </h1>
        <p className="text-gray-600">Advanced security features for enterprise-grade portfolio management</p>
      </div>

      {/* Message Display */}
      {message && (
        <div className={`p-4 rounded-lg border ${
          message.type === 'success' ? 'bg-green-50 border-green-200 text-green-800' :
          message.type === 'error' ? 'bg-red-50 border-red-200 text-red-800' :
          'bg-blue-50 border-blue-200 text-blue-800'
        }`}>
          {message.text}
        </div>
      )}

      {/* Tab Navigation */}
      <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg">
        <button
          onClick={() => setActiveTab('mfa')}
          className={`flex-1 py-2 px-4 rounded-md font-medium transition-all ${
            activeTab === 'mfa'
              ? 'bg-white text-purple-600 shadow-sm'
              : 'text-gray-600 hover:text-gray-800'
          }`}
        >
          <Shield className="inline w-4 h-4 mr-2" />
          Multi-Factor Authentication
        </button>
        <button
          onClick={() => setActiveTab('audit')}
          className={`flex-1 py-2 px-4 rounded-md font-medium transition-all ${
            activeTab === 'audit'
              ? 'bg-white text-purple-600 shadow-sm'
              : 'text-gray-600 hover:text-gray-800'
          }`}
        >
          <Eye className="inline w-4 h-4 mr-2" />
          Audit Logs
        </button>
        <button
          onClick={() => setActiveTab('monitoring')}
          className={`flex-1 py-2 px-4 rounded-md font-medium transition-all ${
            activeTab === 'monitoring'
              ? 'bg-white text-purple-600 shadow-sm'
              : 'text-gray-600 hover:text-gray-800'
          }`}
        >
          <Activity className="inline w-4 h-4 mr-2" />
          Security Monitoring
        </button>
      </div>

      {/* MFA Tab */}
      {activeTab === 'mfa' && (
        <div className="grid md:grid-cols-2 gap-6">
          {/* MFA Setup */}
          <div className="bg-white p-6 rounded-xl shadow-lg border">
            <h3 className="text-xl font-semibold mb-4 flex items-center">
              <Lock className="w-5 h-5 mr-2 text-purple-600" />
              Setup MFA
            </h3>
            <div className="space-y-4">
              <input
                type="text"
                placeholder="Username"
                value={mfaUsername}
                onChange={(e) => setMfaUsername(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
              />
              <input
                type="password"
                placeholder="Password"
                value={mfaPassword}
                onChange={(e) => setMfaPassword(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
              />
              <button
                onClick={setupMfa}
                disabled={loading}
                className="w-full bg-purple-600 text-white py-2 px-4 rounded-lg hover:bg-purple-700 disabled:opacity-50 transition-colors"
              >
                {loading ? 'Setting up...' : 'Setup MFA'}
              </button>
            </div>
          </div>

          {/* MFA Verification */}
          <div className="bg-white p-6 rounded-xl shadow-lg border">
            <h3 className="text-xl font-semibold mb-4 flex items-center">
              <CheckCircle className="w-5 h-5 mr-2 text-green-600" />
              Verify MFA
            </h3>
            <div className="space-y-4">
              <input
                type="text"
                placeholder="Username"
                value={mfaUsername}
                onChange={(e) => setMfaUsername(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
              />
              <input
                type="text"
                placeholder="MFA Code"
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-transparent"
              />
              <div className="flex space-x-2">
                <button
                  onClick={verifyMfa}
                  disabled={loading}
                  className="flex-1 bg-green-600 text-white py-2 px-4 rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
                >
                  {loading ? 'Verifying...' : 'Verify Code'}
                </button>
                <button
                  onClick={generateMfaCode}
                  disabled={loading}
                  className="px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 disabled:opacity-50 transition-colors"
                  title="Generate test MFA code"
                >
                  Generate
                </button>
              </div>
            </div>
          </div>

          {/* MFA Status */}
          <div className="bg-white p-6 rounded-xl shadow-lg border md:col-span-2">
            <h3 className="text-xl font-semibold mb-4 flex items-center">
              <User className="w-5 h-5 mr-2 text-blue-600" />
              MFA Status
            </h3>
            <div className="flex space-x-4 mb-4">
              <input
                type="text"
                placeholder="Username"
                value={mfaUsername}
                onChange={(e) => setMfaUsername(e.target.value)}
                className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
              <button
                onClick={getMfaStatus}
                disabled={loading}
                className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {loading ? 'Loading...' : 'Get Status'}
              </button>
            </div>
            
            {mfaStatus && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="text-center p-3 bg-gray-50 rounded-lg">
                  <div className="text-sm text-gray-600">MFA Enabled</div>
                  <div className={`font-semibold ${mfaStatus.mfaEnabled ? 'text-green-600' : 'text-red-600'}`}>
                    {mfaStatus.mfaEnabled ? 'Yes' : 'No'}
                  </div>
                </div>
                <div className="text-center p-3 bg-gray-50 rounded-lg">
                  <div className="text-sm text-gray-600">Failed Attempts</div>
                  <div className="font-semibold text-gray-800">{mfaStatus.failedAttempts}</div>
                </div>
                <div className="text-center p-3 bg-gray-50 rounded-lg">
                  <div className="text-sm text-gray-600">Account Locked</div>
                  <div className={`font-semibold ${mfaStatus.isLocked ? 'text-red-600' : 'text-green-600'}`}>
                    {mfaStatus.isLocked ? 'Yes' : 'No'}
                  </div>
                </div>
                <div className="text-center p-3 bg-gray-50 rounded-lg">
                  <div className="text-sm text-gray-600">Lockout Time</div>
                  <div className="font-semibold text-gray-800">
                    {mfaStatus.remainingLockoutTime > 0 ? `${mfaStatus.remainingLockoutTime}m` : 'None'}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Audit Tab */}
      {activeTab === 'audit' && (
        <div className="space-y-6">
          {/* Statistics */}
          {auditStats && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="bg-white p-4 rounded-xl shadow-lg border text-center">
                <div className="text-2xl font-bold text-blue-600">{auditStats.totalLogs}</div>
                <div className="text-sm text-gray-600">Total Logs</div>
              </div>
              <div className="bg-white p-4 rounded-xl shadow-lg border text-center">
                <div className="text-2xl font-bold text-red-600">{auditStats.failedActions}</div>
                <div className="text-sm text-gray-600">Failed Actions</div>
              </div>
              <div className="bg-white p-4 rounded-xl shadow-lg border text-center">
                <div className="text-2xl font-bold text-orange-600">{auditStats.securityEvents}</div>
                <div className="text-sm text-gray-600">Security Events</div>
              </div>
              <div className="bg-white p-4 rounded-xl shadow-lg border text-center">
                <div className="text-2xl font-bold text-green-600">{auditStats.todayLogs}</div>
                <div className="text-sm text-gray-600">Today's Logs</div>
              </div>
            </div>
          )}

          {/* Audit Logs */}
          <div className="bg-white rounded-xl shadow-lg border overflow-hidden">
            <div className="p-4 border-b bg-gray-50">
              <div className="flex justify-between items-center">
                <h3 className="text-lg font-semibold">Recent Audit Logs</h3>
                <div className="flex space-x-2">
                  <button
                    onClick={loadRecentAuditLogs}
                    className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                  >
                    Recent
                  </button>
                  <button
                    onClick={loadFailedActions}
                    className="px-3 py-1 text-sm bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
                  >
                    Failed
                  </button>
                  <button
                    onClick={loadSecurityEvents}
                    className="px-3 py-1 text-sm bg-orange-600 text-white rounded hover:bg-orange-700 transition-colors"
                  >
                    Security
                  </button>
                </div>
              </div>
            </div>
            
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">User</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Action</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Severity</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Details</th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {auditLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 text-sm text-gray-900">
                        {formatTimestamp(log.timestamp)}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-900">
                        {log.user?.username || 'Unknown'}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-900">
                        {log.action}
                      </td>
                      <td className="px-4 py-3 text-sm">
                        {log.success ? (
                          <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                            <CheckCircle className="w-3 h-3 mr-1" />
                            Success
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800">
                            <XCircle className="w-3 h-3 mr-1" />
                            Failed
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${getSeverityColor(log.severity)}`}>
                          {log.severity}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-900 max-w-xs truncate">
                        {log.details}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Monitoring Tab */}
      {activeTab === 'monitoring' && (
        <div className="bg-white p-6 rounded-xl shadow-lg border">
          <h3 className="text-xl font-semibold mb-4 flex items-center">
            <AlertTriangle className="w-5 h-5 mr-2 text-orange-600" />
            Security Monitoring
          </h3>
          <div className="grid md:grid-cols-2 gap-6">
            <div className="space-y-4">
              <h4 className="font-medium text-gray-800">Real-time Security Features</h4>
              <ul className="space-y-2 text-sm text-gray-600">
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Multi-Factor Authentication (MFA)
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Comprehensive Audit Logging
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Account Lockout Protection
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  IP Address Tracking
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Session Monitoring
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Role-Based Access Control
                </li>
              </ul>
            </div>
            
            <div className="space-y-4">
              <h4 className="font-medium text-gray-800">Compliance Features</h4>
              <ul className="space-y-2 text-sm text-gray-600">
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  SEC/FINRA Audit Trail
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Data Encryption
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  User Activity Monitoring
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Security Event Alerts
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Compliance Reporting
                </li>
                <li className="flex items-center">
                  <CheckCircle className="w-4 h-4 text-green-500 mr-2" />
                  Risk Assessment Tools
                </li>
              </ul>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SecurityDashboard;

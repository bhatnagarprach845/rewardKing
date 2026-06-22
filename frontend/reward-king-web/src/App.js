import React, { useState, useEffect } from 'react';
import { syncUserWithBackend } from './api/rewardApi';
import { BrowserRouter as Router, Routes, Route, Link, Navigate } from 'react-router-dom';
import { Authenticator } from '@aws-amplify/ui-react';
import { Amplify } from 'aws-amplify';
import '@aws-amplify/ui-react/styles.css';
import Dashboard from './Dashboard';
import FileUpload from './FileUpload';
import AdminDashboard from './AdminDashboard';
import RewardStore from './RewardStore'; // 🚀 IMPORTED YOUR NEW COMPONENT

Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId: 'us-east-2_OfWs7erUH',
      userPoolClientId: '5mm6akdvcc4skv9m2anfgmb1ej',
      loginWith: {
        username: true,
        email: true
      }
    }
  }
});

function App() {
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const handleUploadSuccess = () => {
    console.log("Prachi Parent :: Intercepted upload success event. Bumping state dependency counter...");
    setRefreshTrigger(prev => prev + 1);
  };

  const SyncWrapper = ({ user }) => {
    useEffect(() => {
      const performSync = async () => {
        try {
          await syncUserWithBackend();
          console.log("Prachi :: User profile synchronizations completed with backend ledger.");
        } catch (err) {
          console.error("Prachi :: Profile sync pipeline initialization dropped:", err.message);
        }
      };

      if (user) performSync();
    }, [user]);

    return null;
  };

  return (
    <Router>
      <Authenticator signUpAttributes={['email']}>
        {({ signOut, user }) => {
          const currentUsername = user.username || user.signInDetails?.loginId || "User";
          const isAdmin = currentUsername === 'prachi';

          return (
            <div className="App" style={styles.appContainer}>
              <SyncWrapper user={user} />

              <nav style={styles.nav}>
                <h2 style={{ color: '#28a745', margin: 0, letterSpacing: '0.5px' }}>👑 Cashback King</h2>
                <div style={styles.navLinks}>
                  <Link to="/" style={styles.link}>My Rewards</Link>
                  <Link to="/store" style={styles.link}>Store</Link> {/* Added Quick Store Link */}
                  {isAdmin && (
                    <Link to="/admin" style={styles.adminLink}>🔒 Admin Panel</Link>
                  )}
                  <button onClick={signOut} style={styles.logoutBtn}>Sign Out</button>
                </div>
              </nav>

              <Routes>
                {/* Main Dashboard view path */}
                <Route path="/" element={
                  <main style={{ padding: '20px' }}>
                    <Dashboard refreshTrigger={refreshTrigger} username={currentUsername} />
                    <div style={{ margin: '30px auto', maxWidth: '400px', borderTop: '1px solid #333' }}></div>
                    <FileUpload onUploadSuccess={handleUploadSuccess} />
                  </main>
                } />

                {/* 🚀 REGISTERED THE REWARD STORE ROUTE */}
                <Route path="/store" element={<RewardStore />} />

                {/* Admin authorization guard path */}
                <Route
                  path="/admin"
                  element={isAdmin ? <AdminDashboard /> : <Navigate to="/" replace />}
                />

                {/* Catch-all fallback path */}
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </div>
          );
        }}
      </Authenticator>
    </Router>
  );
}

const styles = {
  appContainer: { backgroundColor: '#1a1a1a', minHeight: '100vh', color: 'white', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' },
  nav: { display: 'flex', justifyContent: 'space-between', padding: '20px 40px', borderBottom: '1px solid #2d2d2d', alignItems: 'center', backgroundColor: '#111' },
  navLinks: { display: 'flex', gap: '25px', alignItems: 'center' },
  link: { color: '#bbb', textDecoration: 'none', fontSize: '15px', fontWeight: '500', transition: 'color 0.2s' },
  adminLink: { color: '#ffc107', fontWeight: 'bold', textDecoration: 'none', fontSize: '15px' },
  logoutBtn: { backgroundColor: 'transparent', color: '#888', border: '1px solid #333', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer', fontSize: '13px', fontWeight: '500', transition: 'all 0.2s' }
};

export default App;
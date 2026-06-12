import React, { useState, useEffect } from 'react';
import { syncUserWithBackend } from './api/cashbackApi';
import { BrowserRouter as Router, Routes, Route, Link, Navigate } from 'react-router-dom';
import { Authenticator } from '@aws-amplify/ui-react';
import { Amplify } from 'aws-amplify';
import '@aws-amplify/ui-react/styles.css';
import Dashboard from './Dashboard';
import FileUpload from './FileUpload';
import AdminDashboard from './AdminDashboard';

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
    setRefreshTrigger(prev => prev + 1);
  };

  const SyncWrapper = ({ user }) => {
    useEffect(() => {
      const performSync = async () => {
        try {
          await syncUserWithBackend(); // Token fetched internally now
          console.log("Prachi :: User synced with backend successfully");
        } catch (err) {
          // Log the FULL error so you can see exactly what AWS rejected
          console.error("Prachi :: Sync failed. Status:", err.message);
        }
      };

      if (user) performSync();
    }, [user]);

    return null;
  };

  return (
    <Router>
      <Authenticator signUpAttributes={['email']}>
        {({ signOut, user }) => (
          <div className="App" style={styles.appContainer}>
            <SyncWrapper user={user} />
            <nav style={styles.nav}>
              <h2 style={{ color: '#28a745', margin: 0 }}>Cashback King</h2>
              <div style={styles.navLinks}>
                <Link to="/" style={styles.link}>My Rewards</Link>
                {user.username === 'prachi' && (
                  <Link to="/admin" style={styles.adminLink}>Admin Panel</Link>
                )}
                <button onClick={signOut} style={styles.logoutBtn}>Sign Out</button>
              </div>
            </nav>

            <Routes>
              <Route path="/" element={
                <main style={{ padding: '20px' }}>
                  <Dashboard refreshTrigger={refreshTrigger} username={user.username} />
                  <div style={{ margin: '30px auto', maxWidth: '400px', borderTop: '1px solid #444' }}></div>
                  <FileUpload onUploadSuccess={handleUploadSuccess} />
                </main>
              } />
              <Route
                path="/admin"
                element={user.username === 'prachi' ? <AdminDashboard /> : <Navigate to="/" replace />}
              />
            </Routes>
          </div>
        )}
      </Authenticator>
    </Router>
  );
}

const styles = {
  appContainer: { backgroundColor: '#1a1a1a', minHeight: '100vh', color: 'white' },
  nav: { display: 'flex', justifyContent: 'space-between', padding: '20px', borderBottom: '1px solid #333', alignItems: 'center' },
  navLinks: { display: 'flex', gap: '20px', alignItems: 'center' },
  link: { color: 'white', textDecoration: 'none' },
  adminLink: { color: '#ffc107', fontWeight: 'bold', textDecoration: 'none' },
  logoutBtn: { backgroundColor: 'transparent', color: '#888', border: '1px solid #444', padding: '5px 10px', borderRadius: '4px', cursor: 'pointer' }
};

export default App;
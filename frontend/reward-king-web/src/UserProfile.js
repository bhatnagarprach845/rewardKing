import React, { useState, useEffect } from 'react';
import { fetchAuthSession } from 'aws-amplify/auth';
import axios from 'axios';

const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1/users/profile`;

const UserProfile = () => {
    const [profile, setProfile] = useState({ name: '', email: '', address: '', phoneNumber: '', upiId: '' });
    const [isEditing, setIsEditing] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    const fetchProfileData = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            if (!token) return;

            const res = await axios.get(BASE_URL, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            let responseData = res.data;
            if (typeof responseData.body === 'string') {
                responseData = JSON.parse(responseData.body);
            }

            setProfile({
                name: responseData.name || '',
                email: responseData.email || '',
                address: responseData.address || '',
                phoneNumber: responseData.phoneNumber || '',
                upiId: responseData.upiId || ''
            });
        } catch (err) {
            console.error("Failed to load user info card parameters:", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { fetchProfileData(); }, []);

    const handleSave = async (e) => {
        e.preventDefault();
        setIsSaving(true);
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();

            await axios.post(`${BASE_URL}/update`, profile, {
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
            });

            alert("🎉 Profile records updated successfully!");
            setIsEditing(false);
        } catch (err) {
            console.error("Profile updates save aborted:", err);
            alert("Could not update shipping cards. Try again.");
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoading) return <p style={{ color: 'white', textAlign: 'center', marginTop: '40px' }}>Loading identity profile...</p>;

    return (
        <div style={styles.container}>
            <div style={styles.headerRow}>
                <button onClick={() => window.location.href = '/'} style={styles.backBtn}>🏡 Dashboard</button>
                <h3 style={{ margin: 0, color: '#333' }}>Account Management</h3>
            </div>

            <div style={styles.profileCard}>
                <div style={styles.avatarCircle}>👤</div>
                <h2 style={styles.userName}>{profile.name || 'Account User'}</h2>
                <p style={styles.userEmail}>{profile.email}</p>

                <form onSubmit={handleSave} style={{ marginTop: '20px' }}>
                    <div style={styles.inputGroup}>
                        <label style={styles.label}>📞 Phone Number</label>
                        <input
                            type="tel"
                            value={profile.phoneNumber}
                            disabled={!isEditing}
                            placeholder="Add your contact phone number"
                            onChange={(e) => setProfile({ ...profile, phoneNumber: e.target.value })}
                            style={{ ...styles.input, backgroundColor: isEditing ? '#fff' : '#f5f5f5' }}
                        />
                    </div>

                    <div style={styles.inputGroup}>
                        <label style={styles.label}>📍 Delivery Shipping Address</label>
                        <textarea
                            rows="3"
                            value={profile.address}
                            disabled={!isEditing}
                            placeholder="Enter full street address, apartment, city, state, zip"
                            onChange={(e) => setProfile({ ...profile, address: e.target.value })}
                            style={{ ...styles.textarea, backgroundColor: isEditing ? '#fff' : '#f5f5f5' }}
                        />
                    </div>

                    <div style={styles.inputGroup}>
                        <label style={styles.label}>💳 UPI Identifier (Optional)</label>
                        <input
                            type="text"
                            value={profile.upiId}
                            disabled={!isEditing}
                            placeholder="username@bankline"
                            onChange={(e) => setProfile({ ...profile, upiId: e.target.value })}
                            style={{ ...styles.input, backgroundColor: isEditing ? '#fff' : '#f5f5f5' }}
                        />
                    </div>

                    {isEditing ? (
                        <div style={{ display: 'flex', gap: '10px', marginTop: '25px' }}>
                            <button type="button" onClick={() => setIsEditing(false)} style={styles.cancelBtn}>Cancel</button>
                            <button type="submit" disabled={isSaving} style={styles.saveBtn}>
                                {isSaving ? 'Saving...' : 'Save Changes'}
                            </button>
                        </div>
                    ) : (
                        <button type="button" onClick={() => setIsEditing(true)} style={styles.editBtn}>
                            📝 Edit Profile Details
                        </button>
                    )}
                </form>
            </div>
        </div>
    );
};

const styles = {
    container: { padding: '30px', maxWidth: '550px', margin: '0 auto', fontFamily: 'Arial, sans-serif' },
    headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '25px', backgroundColor: '#f8f9fa', padding: '12px 20px', borderRadius: '12px', border: '1px solid #eee' },
    backBtn: { backgroundColor: '#fff', border: '1px solid #ccc', padding: '6px 14px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', color: '#555' },
    profileCard: { backgroundColor: '#fff', borderRadius: '16px', padding: '30px', boxShadow: '0 4px 12px rgba(0,0,0,0.15)', textAlign: 'center' },
    avatarCircle: { fontSize: '56px', background: '#e8f5e9', width: '90px', height: '90px', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: '50%', margin: '0 auto 15px auto', border: '2px solid #28a745' },
    userName: { fontSize: '22px', color: '#222', margin: '0 0 4px 0' },
    userEmail: { fontSize: '14px', color: '#666', margin: '0 0 20px 0' },
    inputGroup: { display: 'flex', flexDirection: 'column', textAlign: 'left', marginBottom: '16px' },
    label: { fontSize: '12px', fontWeight: 'bold', color: '#555', marginBottom: '6px', textTransform: 'uppercase' },
    input: { padding: '10px 14px', border: '1px solid #ccc', borderRadius: '8px', fontSize: '14px', color: '#333', outline: 'none', transition: 'border 0.2s' },
    textarea: { padding: '10px 14px', border: '1px solid #ccc', borderRadius: '8px', fontSize: '14px', color: '#333', outline: 'none', resize: 'none', fontFamily: 'inherit' },
    editBtn: { width: '100%', marginTop: '15px', backgroundColor: '#28a745', color: 'white', border: 'none', padding: '12px', borderRadius: '8px', fontWeight: 'bold', fontSize: '14px', cursor: 'pointer' },
    saveBtn: { flex: 2, backgroundColor: '#007bff', color: 'white', border: 'none', padding: '12px', borderRadius: '8px', fontWeight: 'bold', fontSize: '14px', cursor: 'pointer' },
    cancelBtn: { flex: 1, backgroundColor: '#fff', border: '1px solid #ccc', color: '#333', padding: '12px', borderRadius: '8px', fontWeight: 'bold', fontSize: '14px', cursor: 'pointer' }
};

export default UserProfile;
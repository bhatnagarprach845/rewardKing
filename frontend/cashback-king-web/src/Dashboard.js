import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

// 1. Centralized URL Configuration
const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1`; // Most dashboard calls go to receipts path
const UPI_REGEX = /^[\w.-]+@[\w.-]+$/;

const Dashboard = ({ refreshTrigger, username }) => {
    const [data, setData] = useState(null);
    const [isRedeeming, setIsRedeeming] = useState(false);
    const [redeemAmount, setRedeemAmount] = useState("");
    const [upiError, setUpiError] = useState("");
    const [showProfileModal, setShowProfileModal] = useState(false);
    const [profileForm, setProfileForm] = useState({ name: '', upiId: '' });

    const fetchStatus = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();
            if (!token) {
                        console.error("No ID Token found");
                        return;
                    }

            const res = await axios.get(`${BASE_URL}/payout-status`, {
                headers: { 'Authorization': `Bearer ${token}`,
                         'Content-Type': 'application/json' }
            });
            setData(res.data);

            // Trigger modal if UPI is missing
            if (!res.data.upiId) {
                setShowProfileModal(true);
            }
        } catch (err) {


            console.error("Error fetching status", err);
        }
    };

    useEffect(() => {
        fetchStatus();
    }, [refreshTrigger]);

    const handleUpiChange = (e) => {
        const val = e.target.value;
        setProfileForm({ ...profileForm, upiId: val });
        if (!val) {
            setUpiError("");
        } else if (!UPI_REGEX.test(val)) {
            setUpiError("Invalid format. Use example@upi");
        } else {
            setUpiError("");
        }
    };

    const isFormValid = profileForm.name.length > 2 && UPI_REGEX.test(profileForm.upiId);

    const handleProfileSubmit = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();
            await axios.post(`${BASE_URL}/syncProfile`, profileForm, {
                headers: { 'Authorization': `Bearer ${token}`,
                 'Content-Type': 'application/json'
                 }
            });
            setShowProfileModal(false);
            fetchStatus();
        } catch (err) {
            alert("Failed to save profile.");
        }
    };

    const handleRedeem = async (isAll = false) => {
        const amountToSend = isAll ? data.currentBalance : parseFloat(redeemAmount);
        const minLimit = 30.00;

        if (amountToSend < minLimit) {
            alert(`Minimum redemption amount is ₹${minLimit}.`);
            return;
        }

        setIsRedeeming(true);
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();
            console.log ("Dashboard API.js -- BASE_URL :: ", BASE_URL);
            const res = await axios.post(`${BASE_URL}/redeem`,
                { amount: amountToSend },
                { headers: { Authorization: `Bearer ${token}` } }
            );
            if (res.status === 200) {
                alert(`Success! Payout of ₹${amountToSend} initiated.`);
                setRedeemAmount("");
                fetchStatus();
            }
        } catch (err) {
            alert(err.response?.data?.error || "Redemption failed.");
        } finally {
            setIsRedeeming(false);
        }
    };

    if (!data) return <p style={{ color: 'white', textAlign: 'center' }}>Loading your rewards...</p>;

    const progressPercent = Math.min((data.currentBalance / data.threshold) * 100, 100);
    const canRedeem = data.currentBalance >= 30;

    return (
        <> {/* Wrapped in a Fragment to allow two main elements */}
            <div style={styles.card}>
                <div style={{ marginBottom: '15px' }}>
                    <h2 style={{ color: '#28a745', margin: 0 }}>Welcome, {username}!</h2>
                </div>

                <h3 style={{ color: '#333', margin: 0 }}>Your Rewards</h3>
                <p style={styles.balance}>₹{data.currentBalance.toFixed(2)}</p>

                <div style={styles.progressBase}>
                    <div style={{
                        ...styles.progressBar,
                        width: `${progressPercent}%`,
                        backgroundColor: progressPercent >= 100 ? '#28a745' : '#ffc107'
                    }}></div>
                </div>
                <p style={styles.message}>{data.statusMessage}</p>

                <div style={styles.redeemContainer}>
                    <input
                        type="number"
                        placeholder="Min ₹30"
                        value={redeemAmount}
                        onChange={(e) => setRedeemAmount(e.target.value)}
                        style={styles.redeemInput}
                    />
                    <button
                        onClick={() => handleRedeem(false)}
                        disabled={isRedeeming || parseFloat(redeemAmount) < 30 || data.currentBalance < 30}
                        style={{
                            ...styles.redeemBtn,
                            backgroundColor: (parseFloat(redeemAmount) >= 30 && data.currentBalance >= 30) ? '#28a745' : '#ccc'
                        }}
                    >
                        Redeem
                    </button>
                    <button
                        onClick={() => handleRedeem(true)}
                        disabled={isRedeeming || !canRedeem}
                        style={styles.allBtn}
                    >
                        All
                    </button>
                </div>

                <h4 style={{ color: '#333', marginTop: '20px' }}>Recent Activity</h4>
                <ul style={styles.list}>
                    {data.recentTransactions?.map((tx, index) => (
                        <li key={index} style={styles.listItem}>
                            <div style={{ display: 'flex', flexDirection: 'column' }}>
                                <span style={{ fontSize: '13px' }}>
                                    {tx.processedAt ? tx.processedAt.split('T')[0] : 'N/A'}
                                </span>
                                <small style={{ color: '#888', fontSize: '10px' }}>{tx.status}</small>
                            </div>
                            <span style={{
                                fontWeight: 'bold',
                                color: tx.amountAwarded < 0 ? '#dc3545' : '#28a745'
                            }}>
                                {tx.amountAwarded < 0 ? '-' : '+'}₹{Math.abs(tx.amountAwarded).toFixed(2)}
                            </span>
                        </li>
                    ))}
                </ul>
            </div>

            {/* Modal Logic must be inside curly braces and sibling to the card div */}
            {showProfileModal && (
                <div style={styles.modalOverlay}>
                    <div style={styles.modalContent}>
                        <h3>Complete Your Profile</h3>
                        <p>We need your details to send you cashback!</p>

                        <input
                            placeholder="Full Name"
                            value={profileForm.name}
                            onChange={(e) => setProfileForm({ ...profileForm, name: e.target.value })}
                            style={styles.input}
                        />
                        <div style={{ textAlign: 'left', width: '100%' }}>
                            <input
                                placeholder="UPI ID (e.g. prachi@okaxis)"
                                value={profileForm.upiId}
                                onChange={handleUpiChange}
                                style={{
                                    ...styles.input,
                                    borderColor: upiError ? '#dc3545' : '#ccc',
                                    marginBottom: '5px'
                                }}
                            />
                            {upiError && <small style={{ color: '#dc3545', display: 'block', marginBottom: '10px' }}>{upiError}</small>}
                        </div>

                        <button
                            onClick={handleProfileSubmit}
                            disabled={!isFormValid}
                            style={{
                                ...styles.submitBtn,
                                backgroundColor: isFormValid ? '#28a745' : '#ccc',
                                cursor: isFormValid ? 'pointer' : 'not-allowed'
                            }}
                        >
                            Save & Start Earning
                        </button>
                    </div>
                </div>
            )}
        </>
    );
};

const styles = {
    card: { padding: '20px', border: '1px solid #ddd', borderRadius: '12px', maxWidth: '400px', margin: '20px auto', backgroundColor: '#fff', boxShadow: '0 4px 6px rgba(0,0,0,0.1)' },
    balance: { fontSize: '28px', fontWeight: 'bold', margin: '10px 0', color: '#28a745' },
    progressBase: { width: '100%', height: '8px', backgroundColor: '#e0e0e0', borderRadius: '4px', overflow: 'hidden' },
    progressBar: { height: '100%', transition: 'width 0.5s ease' },
    message: { fontSize: '12px', color: '#666', marginTop: '5px' },
    redeemContainer: { display: 'flex', gap: '8px', marginTop: '15px' },
    redeemInput: { flex: 1, padding: '8px', borderRadius: '6px', border: '1px solid #ccc', outline: 'none' },
    redeemBtn: { color: 'white', border: 'none', padding: '8px 12px', borderRadius: '6px', fontWeight: 'bold' },
    allBtn: { backgroundColor: '#007bff', color: 'white', border: 'none', padding: '8px 12px', borderRadius: '6px', cursor: 'pointer' },
    list: { listStyle: 'none', padding: 0, marginTop: '10px', maxHeight: '200px', overflowY: 'auto' },
    listItem: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', borderBottom: '1px solid #eee' },
    modalOverlay: { position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.8)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 },
    modalContent: { backgroundColor: 'white', padding: '30px', borderRadius: '15px', textAlign: 'center', width: '320px' },
    input: { width: '100%', padding: '10px', margin: '10px 0', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box' },
    submitBtn: { width: '100%', padding: '10px', color: 'white', border: 'none', borderRadius: '5px', fontWeight: 'bold' }
};

export default Dashboard;
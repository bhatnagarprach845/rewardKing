import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1`;
const UPI_REGEX = /^[\w.-]+@[\w.-]+$/;

const Dashboard = ({ refreshTrigger, username }) => {
    const [data, setData] = useState(null);
    const [isRedeeming, setIsRedeeming] = useState(false);
    const [redeemAmount, setRedeemAmount] = useState("");
    const [upiError, setUpiError] = useState("");

    // UI state toggles
    const [showProfileModal, setShowProfileModal] = useState(false);
    const [isSaving, setIsSaving] = useState(false);

    // Form fields for adding new targets
    const [newUpi, setNewUpi] = useState("");
    const [selectedUpi, setSelectedUpi] = useState(""); // Tracks the active target for payout

    const fetchStatus = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();
            if (!token) return;

            const res = await axios.get(`${BASE_URL}/payout-status`, {
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
            });
            setData(res.data);

            // Expecting backend to return data.upiIds as an array.
            if (res.data.upiIds && res.data.upiIds.length > 0) {
                setSelectedUpi(res.data.selectedUpi || res.data.upiIds[0]);
            } else if (res.data.upiId) {
                setSelectedUpi(res.data.upiId);
            } else {
                setShowProfileModal(true); // Force open if completely brand new
            }
        } catch (err) {
            console.error("Error fetching status", err);
        }
    };

    useEffect(() => {
        fetchStatus();
    }, [refreshTrigger]);

    const handleAddNewUpiSubmit = async () => {
        if (!UPI_REGEX.test(newUpi)) {
            setUpiError("Invalid format. Use example@upi");
            return;
        }

        setIsSaving(true);
        setUpiError("");
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();

            await axios.post(`${BASE_URL}/addUpiId`, { upiId: newUpi }, {
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
            });

            setNewUpi("");
            await fetchStatus(); // Wait for status to refresh database mapping state
        } catch (err) {
            const serverMsg = err.response?.data?.message || "Verification failed. Invalid UPI Account.";
            setUpiError(serverMsg);
        } finally {
            setIsSaving(false);
        }
    };

    const handleRedeem = async (isAll = false) => {
        const amountToSend = isAll ? data.currentBalance : parseFloat(redeemAmount);
        const minLimit = 30.00;

        if (amountToSend < minLimit) {
            alert(`Minimum redemption amount is ₹${minLimit}.`);
            return;
        }
        if (!selectedUpi) {
            alert("Please select a target UPI address for your payout routing.");
            return;
        }

        setIsRedeeming(true);
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();

            const res = await axios.post(`${BASE_URL}/redeem`,
                { amount: amountToSend, targetUpi: selectedUpi },
                { headers: { Authorization: `Bearer ${token}` } }
            );
            if (res.status === 200) {
                alert(`Success! Payout of ₹${amountToSend} initiated to ${selectedUpi}.`);
                setRedeemAmount("");
                fetchStatus();
            }
        } catch (err) {
            alert(err.response?.data?.error || "Redemption failed.");
        } finally {
            setIsRedeeming(false);
        }
    };

    // EARLY EXIT BLOCK: Check data availability BEFORE declaring dependent calculation properties
    if (!data) return <p style={{ color: 'white', textAlign: 'center' }}>Loading your rewards...</p>;

    // Safe to extract now that data object presence is strictly guaranteed!
    const progressPercent = Math.min((data.currentBalance / data.threshold) * 100, 100);
    const canRedeem = data.currentBalance >= 30;
    const userUpiList = data.upiIds || (data.upiId ? [data.upiId] : []);

    return (
        <>
            <div style={styles.card}>
                {/* Header Section with Profile Button */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                    <h2 style={{ color: '#28a745', margin: 0, fontSize: '18px' }}>Welcome, {username}!</h2>
                    <button onClick={() => setShowProfileModal(true)} style={styles.profileNavBtn}>
                        👤 Profile
                    </button>
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

                {/* Multi-UPI Destination Selection Layout */}
                {userUpiList.length > 0 && (
                    <div style={styles.selectorWrapper}>
                        <h4 style={{ margin: '0 0 8px 0', fontSize: '13px', color: '#555' }}>Select Payout Destination:</h4>
                        {userUpiList.map((id) => (
                            <label key={id} style={{
                                ...styles.radioLabel,
                                backgroundColor: selectedUpi === id ? '#e8f5e9' : '#f9f9f9',
                                border: selectedUpi === id ? '1px solid #28a745' : '1px solid #ddd'
                            }}>
                                <input
                                    type="radio"
                                    name="payoutTarget"
                                    value={id}
                                    checked={selectedUpi === id}
                                    onChange={() => setSelectedUpi(id)} // Direct string injection prevents binding drops
                                    style={{ marginRight: '8px' }}
                                />
                                <span style={{ fontSize: '13px', fontFamily: 'monospace', color: '#333', fontWeight: '500' }}>{id}</span>
                            </label>
                        ))}
                    </div>
                )}

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
                    <button onClick={() => handleRedeem(true)} disabled={isRedeeming || !canRedeem} style={styles.allBtn}>
                        All
                    </button>
                </div>
            </div>

            {/* Profile Detail Management Modal */}
            {showProfileModal && (
                <div style={styles.modalOverlay}>
                    <div style={styles.modalContent}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
                            <h3 style={{ margin: 0, color: '#333' }}>User Profile Details</h3>
                            <button onClick={() => { setShowProfileModal(false); setUpiError(""); }} style={styles.closeModalBtn}>✕</button>
                        </div>

                        <div style={styles.infoBlock}>
                            <p style={{ color: '#333', margin: '4px 0' }}><strong style={{ color: '#555' }}>Name:</strong> {data.name || username}</p>
                            <p style={{ color: '#333', margin: '4px 0' }}><strong style={{ color: '#555' }}>Email:</strong> {data.email || 'N/A'}</p>
                        </div>

                        <h4 style={{ textAlign: 'left', margin: '15px 0 5px 0', color: '#333' }}>Registered Handles:</h4>
                        <div style={styles.upiScrollContainer}>
                            {userUpiList.length === 0 ? (
                                <p style={{ fontSize: '12px', color: '#999' }}>No payment routing added yet.</p>
                            ) : (
                                userUpiList.map(id => (
                                    <div key={id} style={styles.upiBadge}>✓ {id}</div>
                                ))
                            )}
                        </div>

                        <hr style={{ border: '0', borderTop: '1px solid #eee', margin: '15px 0' }} />

                        <h4 style={{ textAlign: 'left', margin: '0 0 5px 0', color: '#333' }}>Link Another UPI ID:</h4>
                        <input
                            placeholder="e.g. name@okaxis"
                            value={newUpi}
                            onChange={(e) => { setNewUpi(e.target.value); setUpiError(""); }}
                            style={styles.input}
                        />
                        {upiError && <small style={styles.errorText}>{upiError}</small>}

                        <button
                            onClick={handleAddNewUpiSubmit}
                            disabled={isSaving || !newUpi}
                            style={{
                                ...styles.submitBtn,
                                backgroundColor: newUpi && !isSaving ? '#28a745' : '#ccc',
                                marginTop: '10px'
                            }}
                        >
                            {isSaving ? 'Validating Account...' : '+ Link New UPI Route'}
                        </button>
                    </div>
                </div>
            )}
        </>
    );
};

const styles = {
    card: { padding: '20px', border: '1px solid #ddd', borderRadius: '12px', maxWidth: '400px', margin: '20px auto', backgroundColor: '#fff', boxShadow: '0 4px 6px rgba(0,0,0,0.1)', color: '#333' },
    profileNavBtn: { backgroundColor: '#f0f0f0', border: '1px solid #ccc', borderRadius: '20px', padding: '6px 12px', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold', color: '#333' },
    balance: { fontSize: '28px', fontWeight: 'bold', margin: '10px 0', color: '#28a745' },
    progressBase: { width: '100%', height: '8px', backgroundColor: '#e0e0e0', borderRadius: '4px', overflow: 'hidden' },
    progressBar: { height: '100%', transition: 'width 0.5s ease' },
    message: { fontSize: '12px', color: '#666', marginTop: '5px' },
    selectorWrapper: { textAlign: 'left', marginTop: '15px', display: 'flex', flexDirection: 'column', gap: '6px' },
    radioLabel: { display: 'flex', alignItems: 'center', padding: '10px', borderRadius: '6px', cursor: 'pointer', transition: 'all 0.2s', color: '#333' },
    redeemContainer: { display: 'flex', gap: '8px', marginTop: '15px' },
    redeemInput: { flex: 1, padding: '8px', borderRadius: '6px', border: '1px solid #ccc', outline: 'none', color: '#333', backgroundColor: '#fff' },
    redeemBtn: { color: 'white', border: 'none', padding: '8px 12px', borderRadius: '6px', fontWeight: 'bold' },
    allBtn: { backgroundColor: '#007bff', color: 'white', border: 'none', padding: '8px 12px', borderRadius: '6px', cursor: 'pointer' },
    modalOverlay: { position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.6)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 },
    modalContent: { backgroundColor: 'white', padding: '25px', borderRadius: '15px', width: '340px', boxShadow: '0 10px 25px rgba(0,0,0,0.2)', color: '#333' },
    closeModalBtn: { background: 'none', border: 'none', fontSize: '16px', cursor: 'pointer', color: '#888' },
    infoBlock: { textAlign: 'left', backgroundColor: '#f8f9fa', padding: '12px 15px', borderRadius: '8px', fontSize: '14px', lineHeight: '1.6' },
    upiScrollContainer: { display: 'flex', flexDirection: 'column', gap: '5px', maxHeight: '100px', overflowY: 'auto' },
    upiBadge: { textAlign: 'left', fontSize: '13px', padding: '8px 12px', backgroundColor: '#f1f3f4', borderRadius: '6px', fontFamily: 'monospace', color: '#333', fontWeight: '500' },
    input: { width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box', color: '#333', backgroundColor: '#fff' },
    errorText: { color: '#dc3545', display: 'block', textAlign: 'left', marginTop: '4px', fontSize: '11px' },
    submitBtn: { width: '100%', padding: '10px', color: 'white', border: 'none', borderRadius: '5px', fontWeight: 'bold', cursor: 'pointer' }
};

export default Dashboard;
import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1/admin`;
console.log("Prachi :: Connecting to backend at:", BASE_URL);

const AdminDashboard = () => {
    // --- State Management ---
    const [wallets, setWallets] = useState([]);
    const [payouts, setPayouts] = useState([]);
    const [selectedUser, setSelectedUser] = useState(null);
    const [viewMode, setViewMode] = useState('list');
    const [userReceipts, setUserReceipts] = useState([]);
    const [userDetails, setUserDetails] = useState(null);
    const [selectedReceipt, setSelectedReceipt] = useState(null);
    const [userHistory, setUserHistory] = useState([]);
    const [isSyncing, setIsSyncing] = useState(false);
    const [animatingBalance, setAnimatingBalance] = useState(null);

    const getAuthHeader = async () => {
        const session = await fetchAuthSession();
        const token = session.tokens?.idToken?.toString();
        return { Authorization: `Bearer ${token}` };
    };

    // --- Helper: Status Styling ---
    const getStatusStyle = (status) => {
        const styles = {
            'REDEEMED': { backgroundColor: '#f39c12', color: '#fff' },
            'APPROVED': { backgroundColor: '#3498db', color: '#fff' },
            'SETTLED':  { backgroundColor: '#27ae60', color: '#fff' },
            'FAILED':   { backgroundColor: '#e74c3c', color: '#fff' },
        };
        return styles[status] || { backgroundColor: '#444', color: '#ccc' };
    };

    // --- API Methods ---
    const fetchInitialData = async () => {
        try {
            const headers = await getAuthHeader();
            const [walletRes, payoutRes] = await Promise.all([
                axios.get(`${BASE_URL}/wallets`, { headers }),
                axios.get(`${BASE_URL}/payouts`, { headers })
            ]);
            setWallets(walletRes.data);
            setPayouts(payoutRes.data);
        } catch (err) {
            console.error("Dashboard load failed", err);
        }
    };

    const fetchUserDrillDown = useCallback(async () => {
        if (!selectedUser) return;
        const id = selectedUser.id;

        try {
            const headers = await getAuthHeader();
            if (viewMode === 'activity') {
                const res = await axios.get(`${BASE_URL}/users/${id}/transactions`, { headers });
                setUserHistory(res.data);
            } else if (viewMode === 'profile') {
                const res = await axios.get(`${BASE_URL}/users/${id}/profile`, { headers });
                setUserDetails(res.data);
            } else if (viewMode === 'receipts') {
                const res = await axios.get(`${BASE_URL}/users/${id}/receipts`, { headers });
                setUserReceipts(Array.isArray(res.data) ? res.data : []);
            }
        } catch (err) {
            console.error(`Failed to fetch ${viewMode} data`, err);
        }
    }, [selectedUser, viewMode]);

    // --- Effects ---
    useEffect(() => { fetchInitialData(); }, []);
    useEffect(() => { fetchUserDrillDown(); }, [fetchUserDrillDown]);

    // --- Animation Logic ---
    const animateValue = (start, end, duration, setter) => {
        let startTimestamp = null;
        const step = (timestamp) => {
            if (!startTimestamp) startTimestamp = timestamp;
            const progress = Math.min((timestamp - startTimestamp) / duration, 1);
            const current = Math.floor(progress * (end - start) + start);
            setter(current);
            if (progress < 1) window.requestAnimationFrame(step);
        };
        window.requestAnimationFrame(step);
    };

    // --- Action Handlers ---
    const handleUserClick = (userId, fullName) => {
        setSelectedUser({ id: userId, name: fullName });
        setViewMode('profile');
    };

    const handleApprovePayout = async (transactionId, amount) => {
        const absAmount = Math.abs(amount);
        if (!window.confirm(`Approve payment of ₹${absAmount}?`)) return;

        try {
            const headers = await getAuthHeader();
            const res = await axios.post(`${BASE_URL}/payouts/approve/${transactionId}`, {}, { headers });
            setIsSyncing(true);
            animateValue(absAmount, 0, 1000, setAnimatingBalance);

            setTimeout(async () => {
                await Promise.all([
                    fetchInitialData(),
                    fetchUserDrillDown()
                ]);
                setIsSyncing(false);
                setAnimatingBalance(null);
                alert(`Success! Payout ID: ${res.data.id || res.data}`);
            }, 1200);
        } catch (err) {
            setIsSyncing(false);
            setAnimatingBalance(null);
            alert("Error: " + (err.response?.data || "Server unreachable"));
        }
    };

    const downloadReceiptItems = (receipt) => {
        const headers = "Description,Quantity,Unit Price,Total Price\n";
        const rows = receipt.items.map(i =>
            `"${i.description}",${i.quantity},${i.unitPrice},${i.totalPrice}`
        ).join("\n");

        const blob = new Blob([headers + rows], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `items_${receipt.merchantName}_${receipt.id}.csv`;
        link.click();
    };

    const handleDownload = async (endpoint, fileName) => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();

            const response = await axios.get(`${BASE_URL}${endpoint}`, {
                headers: { Authorization: `Bearer ${token}` },
                responseType: 'blob',
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', fileName);
            document.body.appendChild(link);
            link.click();

            link.parentNode.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error("Prachi :: Download failed", err);
            alert("Could not download report. Check admin permissions.");
        }
    };

    // --- Sub-Components (Render Helpers) ---
    const renderProfileTab = () => {
        if (!userDetails) return null;
        const userWallet = wallets.find(w => String(w.userId) === String(selectedUser.id));
        const balance = userWallet?.currentBalance || 0;
        const pendingReq = payouts.find(p => p.userId === selectedUser.id && (p.status === 'REDEEMED' || p.status === 'PENDING'));
        const absoluteAmount = pendingReq ? Math.abs(pendingReq.amountAwarded) : 0;

        return (
            <div style={styles.contentBox}>
                <p><strong>Email:</strong> {userDetails.email}</p>
                <p><strong>UPI ID:</strong> {userDetails.upiId}</p>
                <p><strong>Wallet Balance:</strong>
                    <span style={{ color: animatingBalance !== null ? '#dc3545' : '#28a745', fontWeight: 'bold', marginLeft: '10px' }}>
                        ₹{animatingBalance !== null ? animatingBalance : balance.toFixed(2)}
                    </span>
                </p>
                <p><strong>Razorpay ID:</strong> {isSyncing ? "Syncing..." : (userDetails.razorpayFundAccountId || 'Not Created')}</p>

                {pendingReq ? (
                    <button
                        onClick={() => handleApprovePayout(pendingReq.id, absoluteAmount)}
                        style={{ ...styles.payoutBtn, width: '100%', marginTop: '10px', opacity: absoluteAmount < 1 ? 0.5 : 1 }}
                        disabled={absoluteAmount < 1}
                    >
                        {absoluteAmount < 1 ? `Low Balance (₹${absoluteAmount})` : `Approve & Pay ₹${absoluteAmount}`}
                    </button>
                ) : <p style={{ color: '#888', fontStyle: 'italic' }}>No pending redemptions.</p>}
            </div>
        );
    };

    return (
        <div style={styles.adminContainer}>
            <h2 style={{ color: '#28a745' }}>System Overview (Admin)</h2>

            {viewMode !== 'list' && selectedUser ? (
                <div style={styles.detailView}>
                    <button onClick={() => { setSelectedUser(null); setViewMode('list'); }} style={styles.backBtn}>← Back</button>
                    <h3 style={{ color: '#fff' }}>User: {selectedUser.name}</h3>

                    <div style={styles.tabGroup}>
                        {['profile', 'receipts', 'activity'].map(tab => (
                            <button
                                key={tab}
                                onClick={() => setViewMode(tab)}
                                style={viewMode === tab ? styles.activeTab : styles.tab}
                            >
                                {tab.charAt(0).toUpperCase() + tab.slice(1)}
                            </button>
                        ))}
                    </div>

                    {viewMode === 'profile' && renderProfileTab()}

                    {viewMode === 'activity' && (
                        <div style={styles.contentBox}>
                            <table style={styles.table}>
                                <thead>
                                    <tr style={styles.headerRow}>
                                        <th>Date</th>
                                        <th>Type</th>
                                        <th>Amount</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {userHistory.map(tx => (
                                        <tr key={tx.id} style={styles.row}>
                                            <td>{new Date(tx.processedAt).toLocaleDateString()}</td>
                                            <td>{tx.amountAwarded < 0 ? "💸 Payout" : "💰 Cashback"}</td>
                                            <td style={{ color: tx.amountAwarded < 0 ? '#e74c3c' : '#27ae60', fontWeight: 'bold' }}>
                                                ₹{Math.abs(tx.amountAwarded)}
                                            </td>
                                            <td><span style={{ ...styles.statusBadge, ...getStatusStyle(tx.status) }}>{tx.status}</span></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {viewMode === 'receipts' && (
                        <div style={styles.contentBox}>
                            {userReceipts.length > 0 ? (
                                <table style={styles.table}>
                                    <thead><tr style={styles.headerRow}><th>Merchant</th><th>Amount</th><th>Status</th></tr></thead>
                                    <tbody>
                                        {userReceipts.map(r => (
                                            <tr key={r.id} style={styles.row}>
                                                <td><button onClick={() => setSelectedReceipt(r)} style={styles.linkButton}>{r.merchantName}</button></td>
                                                <td>₹{r.totalAmount?.toFixed(2)}</td>
                                                <td><span style={styles.statusBadge}>{r.status}</span></td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            ) : <p>No receipts found.</p>}
                        </div>
                    )}
                </div>
            ) : (
                <>
                    <div style={styles.buttonGroup}>
                        <button onClick={() => handleDownload('/wallets/export', 'wallet_report.csv')} style={styles.downloadBtn}>Wallet Report</button>
                        <button onClick={() => handleDownload('/payouts/report', 'payout_report.csv')} style={styles.payoutBtn}>Payout CSV</button>
                    </div>

                    <h3 style={styles.subTitle}>Active Wallets</h3>
                    <table style={styles.table}>
                        <thead><tr style={styles.headerRow}><th>Name</th><th>UPI</th><th>Balance</th></tr></thead>
                        <tbody>
                            {wallets.map(w => {
                                // FIXED: Changed to block scope curly brace to allow constant variables
                                const hasPending = payouts.some(p => p.userId === w.userId && (p.status === 'REDEEMED' || p.status === 'PENDING'));
                                return (
                                    <tr key={w.userId} style={styles.row}>
                                        <td>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                <button onClick={() => handleUserClick(w.userId, w.fullName)} style={styles.linkButton}>
                                                    {w.fullName}
                                                </button>

                                                {/* Visual Indicator Pill */}
                                                {hasPending && (
                                                    <span style={{
                                                        backgroundColor: '#dc3545',
                                                        color: 'white',
                                                        padding: '2px 8px',
                                                        borderRadius: '12px',
                                                        fontSize: '10px',
                                                        fontWeight: 'bold',
                                                        textTransform: 'uppercase',
                                                        letterSpacing: '0.5px',
                                                        boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                                                    }}>
                                                        Pending 💸
                                                    </span>
                                                )}
                                            </div>
                                        </td>
                                        <td>{w.upiId || 'N/A'}</td>
                                        <td>₹{w.currentBalance?.toFixed(2)}</td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </>
            )}

            {/* Receipt Modal */}
            {selectedReceipt && (
                <div style={styles.modalOverlay}>
                    <div style={styles.modalContent}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid #ddd' }}>
                            <h3 style={{color: '#333'}}>{selectedReceipt.merchantName}</h3>
                            <button onClick={() => setSelectedReceipt(null)} style={{ border: 'none', background: 'none', cursor: 'pointer' }}>✖</button>
                        </div>
                        <table style={{ width: '100%', marginTop: '15px', color: '#333' }}>
                            <tbody>
                                {selectedReceipt.items?.map(item => (
                                    <tr key={item.id}><td>{item.description}</td><td>x{item.quantity}</td><td>₹{item.totalPrice}</td></tr>
                                ))}
                            </tbody>
                        </table>
                        <button onClick={() => downloadReceiptItems(selectedReceipt)} style={{ ...styles.downloadBtn, width: '100%', marginTop: '15px' }}>Download CSV</button>
                    </div>
                </div>
            )}
        </div>
    );
};

const styles = {
    adminContainer: { marginTop: '40px', padding: '30px', backgroundColor: '#1a1a1a', borderRadius: '12px', minHeight: '80vh' },
    subTitle: { color: '#ccc', borderBottom: '1px solid #444', paddingBottom: '10px', marginTop: '30px' },
    buttonGroup: { display: 'flex', gap: '15px', marginBottom: '20px' },
    table: { width: '100%', borderCollapse: 'collapse', color: 'white', marginBottom: '20px' },
    headerRow: { backgroundColor: '#333', textAlign: 'left' },
    row: { borderBottom: '1px solid #333', height: '45px' },
    downloadBtn: { backgroundColor: '#007bff', color: 'white', border: 'none', padding: '10px 15px', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold'},
    payoutBtn: { backgroundColor: '#28a745', color: 'white', border: 'none', padding: '10px 15px', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' },
    statusBadge: { backgroundColor: '#444', padding: '2px 8px', borderRadius: '4px', fontSize: '11px', color: '#ffc107' },
    linkButton: { background: 'none', border: 'none', color: '#28a745', textDecoration: 'underline', cursor: 'pointer', fontWeight: 'bold' },
    detailView: { backgroundColor: '#222', padding: '20px', borderRadius: '8px' },
    backBtn: { backgroundColor: '#666', color: 'white', border: 'none', padding: '5px 10px', borderRadius: '4px', cursor: 'pointer', marginBottom: '15px' },
    tabGroup: { display: 'flex', gap: '10px', marginBottom: '20px' },
    tab: { backgroundColor: '#333', color: '#ccc', border: 'none', padding: '10px 20px', cursor: 'pointer' },
    activeTab: { backgroundColor: '#28a745', color: 'white', border: 'none', padding: '10px 20px', fontWeight: 'bold' },
    contentBox: { padding: '20px', border: '1px solid #444', color: '#eee' },
    modalOverlay: { position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.8)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 },
    modalContent: { backgroundColor: 'white', padding: '25px', borderRadius: '12px', width: '90%', maxWidth: '500px' }
};

export default AdminDashboard;
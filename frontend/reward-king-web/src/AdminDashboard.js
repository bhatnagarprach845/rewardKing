import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

// 🚀 PATH RECONCILIATION: AdminDashboard prefix aligns base calls to standard routing context configurations
const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1`;
console.log("Prachi Admin Dashboard :: System connected base route target:", BASE_URL);

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
        const token = session.tokens?.accessToken?.toString();
        return { Authorization: `Bearer ${token}` };
    };

    // --- Helper: Status Styling ---
    const getStatusStyle = (status) => {
        const styles = {
            'REDEEMED': { backgroundColor: '#f39c12', color: '#fff' },
            'PENDING':  { backgroundColor: '#f39c12', color: '#fff' },
            'APPROVED': { backgroundColor: '#3498db', color: '#fff' },
            'COMPLETED':{ backgroundColor: '#27ae60', color: '#fff' },
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
                axios.get(`${BASE_URL}/admin/wallets`, { headers }), // Kept /admin explicitly if your Admin controllers have a secondary prefix filter
                axios.get(`${BASE_URL}/admin/payouts`, { headers })
            ]);

            let walletData = walletRes.data;
            if (walletData && typeof walletData.body === 'string') {
                walletData = JSON.parse(walletData.body);
            }
            const verifiedWallets = Array.isArray(walletData) ? walletData : (walletData.wallets || []);

            let payoutData = payoutRes.data;
            if (payoutData && typeof payoutData.body === 'string') {
                payoutData = JSON.parse(payoutData.body);
            }
            const verifiedPayouts = Array.isArray(payoutData) ? payoutData : (payoutData.payouts || []);

            setWallets(verifiedWallets);
            setPayouts(verifiedPayouts);
        } catch (err) {
            console.error("Dashboard load failed", err);
            setWallets([]);
            setPayouts([]);
        }
    };

    const fetchUserDrillDown = useCallback(async () => {
        if (!selectedUser) return;
        const id = selectedUser.id;

        try {
            const headers = await getAuthHeader();
            if (viewMode === 'activity') {
                const res = await axios.get(`${BASE_URL}/admin/users/${id}/transactions`, { headers });
                let txData = res.data;
                if (txData && typeof txData.body === 'string') txData = JSON.parse(txData.body);
                setUserHistory(Array.isArray(txData) ? txData : []);
            } else if (viewMode === 'profile') {
                const res = await axios.get(`${BASE_URL}/admin/users/${id}/profile`, { headers });
                let profileData = res.data;
                if (profileData && typeof profileData.body === 'string') profileData = JSON.parse(profileData.body);
                setUserDetails(profileData);
            } else if (viewMode === 'receipts') {
                const res = await axios.get(`${BASE_URL}/admin/users/${id}/receipts`, { headers });
                let receiptData = res.data;
                if (receiptData && typeof receiptData.body === 'string') receiptData = JSON.parse(receiptData.body);
                setUserReceipts(Array.isArray(receiptData) ? receiptData : []);
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
        if (!window.confirm(`Approve shipment & fulfill order value of ${absAmount} pts?`)) return;

        try {
            const headers = await getAuthHeader();
            // 🚀 FIXED PATH: Corrected URL path mapping dynamically to remove broken admin folders
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
                alert(`Order Approved Successfully! Transaction Hook Ref: ${res.data.id || transactionId}`);
            }, 1200);
        } catch (err) {
            setIsSyncing(false);
            setAnimatingBalance(null);
            alert("Error: " + (err.response?.data?.error || "Server processing validation failed. Check mappings."));
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
            const token = session.tokens?.accessToken?.toString();

            const response = await axios.get(`${BASE_URL}/admin${endpoint}`, {
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
        const userWallet = (wallets || []).find(w => String(w.userId) === String(selectedUser.id));

        const balance = userWallet?.availablePoints || userWallet?.currentBalance || 0;
        const pendingReq = (payouts || []).find(p => p.userId === selectedUser.id && (p.status === 'REDEEMED' || p.status === 'PENDING'));
        const absoluteAmount = pendingReq ? Math.abs(pendingReq.amountAwarded || pendingReq.pointsAmount || pendingReq.amount || 0) : 0;

        return (
            <div style={styles.contentBox}>
                <p><strong>Email:</strong> {userDetails.email}</p>
                <p><strong>UPI ID:</strong> {userDetails.upiId || 'N/A'}</p>
                <p><strong>Wallet Balance:</strong>
                    <span style={{ color: animatingBalance !== null ? '#dc3545' : '#28a745', fontWeight: 'bold', marginLeft: '10px' }}>
                        {animatingBalance !== null ? `${animatingBalance} pts` : `${balance.toLocaleString()} pts`}
                    </span>
                </p>

                {pendingReq ? (
                    <div style={{ border: '1px dashed #f39c12', padding: '15px', marginTop: '15px', borderRadius: '8px', backgroundColor: '#2c1d0a' }}>
                        <p style={{ color: '#f39c12', margin: '0 0 8px 0', fontWeight: 'bold' }}>⚠️ Pending Fulfillment Queue Notice</p>
                        <p><strong>Item Catalog Identifiers:</strong> {pendingReq.notes || 'Redemption Merchandise Package'}</p>
                        <p><strong>Deduction Cost Hold:</strong> {absoluteAmount} pts</p>

                        <button
                            onClick={() => handleApprovePayout(pendingReq.id, absoluteAmount)}
                            style={{ ...styles.payoutBtn, width: '100%', marginTop: '12px' }}
                        >
                            Approve & Release Shipment
                        </button>
                    </div>
                ) : <p style={{ color: '#888', fontStyle: 'italic', marginTop: '15px' }}>No items inside processing lines or queues currently.</p>}
            </div>
        );
    };

    return (
        <div style={styles.adminContainer}>
            <h2 style={{ color: '#28a745' }}>System Overview (Admin)</h2>

            {viewMode !== 'list' && selectedUser ? (
                <div style={styles.detailView}>
                    <button onClick={() => { setSelectedUser(null); setViewMode('list'); }} style={styles.backBtn}>← Back</button>
                    <h3 style={{ color: '#fff' }}>User Identity: {selectedUser.name}</h3>

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
                                        <th>Notes</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {(userHistory || []).map(tx => {
                                        const value = tx.amountAwarded !== undefined ? tx.amountAwarded : (tx.amount || tx.pointsAmount || 0);
                                        return (
                                            <tr key={tx.id} style={styles.row}>
                                                <td>{tx.date || (tx.processedAt ? new Date(tx.processedAt).toLocaleDateString() : 'Recent')}</td>
                                                <td>{tx.type || 'REDEEMED'}</td>
                                                <td style={{ color: value < 0 || tx.type === 'REDEEMED' ? '#e74c3c' : '#27ae60', fontWeight: 'bold' }}>
                                                    {value < 0 ? '' : '-'}{Math.abs(value)} pts
                                                </td>
                                                <td style={{ fontSize: '12px', color: '#aaa' }}>{tx.notes || 'N/A'}</td>
                                                <td><span style={{ ...styles.statusBadge, ...getStatusStyle(tx.status) }}>{tx.status || 'PENDING'}</span></td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {viewMode === 'receipts' && (
                        <div style={styles.contentBox}>
                            {(userReceipts || []).length > 0 ? (
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
                            ) : <p>No receipt lines indexed for profiling.</p>}
                        </div>
                    )}
                </div>
            ) : (
                <>
                    <div style={styles.buttonGroup}>
                        <button onClick={() => handleDownload('/wallets/export', 'wallet_report.csv')} style={styles.downloadBtn}>Wallet Report</button>
                        <button onClick={() => handleDownload('/payouts/report', 'payout_report.csv')} style={styles.payoutBtn}>Payout CSV</button>
                    </div>

                    <h3 style={styles.subTitle}>Active Ledger Wallets</h3>
                    <table style={styles.table}>
                        <thead><tr style={styles.headerRow}><th>Name</th><th>User Identity UUID Key</th><th>Balance</th></tr></thead>
                        <tbody>
                            {(wallets && Array.isArray(wallets) ? wallets : []).map(w => {
                                const hasPending = (payouts || []).some(p => p.userId === w.userId && (p.status === 'REDEEMED' || p.status === 'PENDING'));
                                const walletBalance = w.availablePoints !== undefined ? w.availablePoints : (w.currentBalance || 0);
                                return (
                                    <tr key={w.userId} style={styles.row}>
                                        <td>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                <button onClick={() => handleUserClick(w.userId, w.fullName)} style={styles.linkButton}>
                                                    {w.fullName || 'Anonymous User'}
                                                </button>

                                                {hasPending && (
                                                    <span style={{
                                                        backgroundColor: '#dc3545',
                                                        color: 'white',
                                                        padding: '2px 8px',
                                                        borderRadius: '12px',
                                                        fontSize: '10px',
                                                        fontWeight: 'bold',
                                                        textTransform: 'uppercase'
                                                    }}>
                                                        Pending 💸
                                                    </span>
                                                )}
                                            </div>
                                        </td>
                                        <td style={{ fontSize: '11px', color: '#777' }}>{w.userId}</td>
                                        <td style={{ fontWeight: 'bold', color: '#28a745' }}>{walletBalance.toLocaleString()} pts</td>
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
                                {(selectedReceipt.items || []).map(item => (
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
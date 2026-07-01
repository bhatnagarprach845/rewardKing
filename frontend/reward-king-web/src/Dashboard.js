import React, { useState, useEffect } from 'react';
import apiClient from './apiClient';

const Dashboard = ({ refreshTrigger, username }) => {
    const [data, setData] = useState(null);
    const [activeFulfillmentTab, setActiveFulfillmentTab] = useState('ACTIVE'); // ACTIVE or HISTORY

    useEffect(() => {
        const fetchDashboardData = async () => {
            try {
                const res = await apiClient.get('/payout-status');
                setData(res.data);
            } catch (err) {
                console.error("Failed to load dashboard balances:", err);
            }
        };
        fetchDashboardData();
    }, [refreshTrigger]);

    if (!data) return <p style={{ color: 'white', textAlign: 'center', marginTop: '40px' }}>Loading your dashboard...</p>;

    const currentPoints = data.currentBalance !== undefined ? data.currentBalance : (data.availablePoints || 0);
    const dynamicMilestoneTarget = 1500;
    const progressPercent = Math.max(0, Math.min((currentPoints / dynamicMilestoneTarget) * 100, 100));

    // Extract transaction tracking streams
    const allTransactions = data.recentTransactions || [];
    const allRedeemed = allTransactions.filter(tx => tx.type === 'REDEEMED');
    const activeOrders = allRedeemed.filter(tx => tx.status !== 'DELIVERED');
    const historicOrders = allRedeemed.filter(tx => tx.status === 'DELIVERED');

    return (
        <div style={{ maxWidth: '450px', margin: '0 auto' }}>
            {/* Balance Overview Card */}
            <div style={styles.card}>
                <h2>Welcome, {username}!</h2>
                <h3>Available Points Balance</h3>
                <p style={styles.balance}>{currentPoints.toLocaleString()} <span style={{ fontSize: '16px', color: '#666' }}>pts</span></p>

                <div style={styles.progressBase}>
                    <div style={{ ...styles.progressBar, width: `${progressPercent}%` }}></div>
                </div>
                <p style={{ fontSize: '11px', marginTop: '5px' }}>
                    Target Milestone Runway Tracker: Progress toward {dynamicMilestoneTarget} pts
                </p>
                <button onClick={() => window.location.href='/store'} style={styles.shopBtn}>Spend Points</button>
            </div>

            {/* 📦 MODULE A: Merchandise Fulfillment Delivery Statuses */}
            <div style={styles.card}>
                <h3>📦 Merchandise Tracking</h3>
                <div style={{ display: 'flex', gap: '10px', marginBottom: '15px' }}>
                    <button
                        onClick={() => setActiveFulfillmentTab('ACTIVE')}
                        style={{ ...styles.tabBtn, backgroundColor: activeFulfillmentTab === 'ACTIVE' ? '#28a745' : '#eee', color: activeFulfillmentTab === 'ACTIVE' ? '#fff' : '#333' }}
                    >
                        Active ({activeOrders.length})
                    </button>
                    <button
                        onClick={() => setActiveFulfillmentTab('HISTORY')}
                        style={{ ...styles.tabBtn, backgroundColor: activeFulfillmentTab === 'HISTORY' ? '#28a745' : '#eee', color: activeFulfillmentTab === 'HISTORY' ? '#fff' : '#333' }}
                    >
                        Fulfillment History ({historicOrders.length})
                    </button>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {(activeFulfillmentTab === 'ACTIVE' ? activeOrders : historicOrders).length === 0 ? (
                        <p style={{ fontSize: '12px', color: '#888', textAlign: 'center', margin: '10px 0' }}>No orders listed in this archive folder.</p>
                    ) : (
                        (activeFulfillmentTab === 'ACTIVE' ? activeOrders : historicOrders).map(tx => (
                            <div key={tx.id} style={styles.txRow}>
                                <div style={{ textAlign: 'left' }}>
                                    <strong>Order ID: #{tx.id}</strong>
                                    <div style={{ fontSize: '12px', color: '#555' }}>{tx.notes}</div>
                                    {tx.trackingNumber && (
                                        <div style={{ marginTop: '5px', fontSize: '11px' }}>
                                            Link: <a href={tx.trackingNumber} target="_blank" rel="noreferrer" style={{ color: '#007bff', fontWeight: 'bold' }}>
                                                Track Package 🚚
                                            </a>
                                        </div>
                                    )}
                                </div>
                                <div style={{ textTransform: 'uppercase', fontSize: '12px', color: '#f39c12', fontWeight: 'bold' }}>
                                    {tx.status}
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            {/* 📜 MODULE B: Complete Points Statement Ledger (Credits & Debits) */}
            <div style={styles.card}>
                <h3>📜 Points Ledger History</h3>
                <div style={styles.scrollContainer}>
                    {allTransactions.length === 0 ? (
                        <p style={{ fontSize: '12px', color: '#888', textAlign: 'center', margin: '20px 0' }}>No points movements logged yet.</p>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            {allTransactions.map((tx) => {
                                const isCredit = tx.type === 'EARNED' || tx.type === 'CREDIT';
                                const rawAmount = tx.amount !== undefined ? tx.amount : (tx.pointsAmount !== undefined ? tx.pointsAmount : 0);

                                return (
                                    <div key={tx.id || Math.random()} style={styles.ledgerRow}>
                                        <div style={{ textAlign: 'left', maxWidth: '70%' }}>
                                            <span style={{
                                                ...styles.badge,
                                                backgroundColor: isCredit ? '#e8f5e9' : '#ffebee',
                                                color: isCredit ? '#28a745' : '#dc3545'
                                            }}>
                                                {tx.type}
                                            </span>
                                            <div style={{ fontSize: '12px', color: '#333', fontWeight: 'bold', marginTop: '6px' }}>
                                                {tx.notes || (isCredit ? 'Receipt Scanning Allocation' : 'Store Reward Checkout')}
                                            </div>
                                            <div style={{ fontSize: '11px', color: '#888', marginTop: '3px' }}>
                                                {tx.date || 'Recent Transaction'}
                                            </div>
                                        </div>
                                        <div style={{ textAlign: 'right' }}>
                                            <span style={{
                                                fontSize: '14px',
                                                fontWeight: 'bold',
                                                color: isCredit ? '#28a745' : '#dc3545'
                                            }}>
                                                {isCredit ? '+' : '-'} {Math.round(rawAmount).toLocaleString()} pts
                                            </span>
                                            <div style={{ fontSize: '10px', color: '#777', marginTop: '4px', textTransform: 'uppercase', fontWeight: 'bold' }}>
                                                {tx.status}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

const styles = {
    card: { padding: '20px', border: '1px solid #ddd', borderRadius: '12px', backgroundColor: '#fff', color: '#333', marginBottom: '15px', boxShadow: '0 4px 8px rgba(0,0,0,0.05)' },
    balance: { fontSize: '32px', fontWeight: 'bold', color: '#28a745', margin: '10px 0' },
    progressBase: { width: '100%', height: '8px', backgroundColor: '#e0e0e0', borderRadius: '4px', overflow: 'hidden' },
    progressBar: { height: '100%', backgroundColor: '#28a745', transition: 'width 0.5s' },
    shopBtn: { width: '100%', color: 'white', border: 'none', padding: '10px', borderRadius: '8px', fontWeight: 'bold', backgroundColor: '#28a745', marginTop: '15px', cursor: 'pointer' },
    tabBtn: { border: 'none', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '12px' },
    txRow: { border: '1px solid #eee', padding: '10px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#f9f9f9' },
    ledgerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px', backgroundColor: '#fafafa', borderRadius: '8px', border: '1px solid #f0f0f0' },
    badge: { fontSize: '9px', fontWeight: 'bold', padding: '2px 6px', borderRadius: '4px', letterSpacing: '0.5px' },
    scrollContainer: { maxHeight: '250px', overflowY: 'auto', paddingRight: '4px' }
};

export default Dashboard;
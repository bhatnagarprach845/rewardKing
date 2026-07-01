import React, { useState, useEffect } from 'react';
import apiClient from './apiClient';

const Dashboard = ({ refreshTrigger, username }) => {
    const [data, setData] = useState(null);
    const [activeFulfillmentTab, setActiveFulfillmentTab] = useState('ACTIVE'); // ACTIVE or HISTORY

    useEffect(() => {
        const fetchDashboardData = async () => {
            try {
                const res = await apiClient.get('/api/v1/payout-status');
                setData(res.data);
            } catch (err) {
                console.error("Failed to load dashboard balances:", err);
            }
        };
        fetchDashboardData();
    }, [refreshTrigger]);

    if (!data) return <p style={{ color: 'white', textAlign: 'center' }}>Loading your dashboard...</p>;

    const currentPoints = data.currentBalance !== undefined ? data.currentBalance : (data.availablePoints || 0);

    // 🚀 UX DYNAMIC MILESTONE EXT指标: Set target to 1.5x milestone baseline thresholds
    const dynamicMilestoneTarget = 1500;
    const progressPercent = Math.min((currentPoints / dynamicMilestoneTarget) * 100, 100);

    // Filter sub-arrays based on fulfillment status tabs
    const allRedeemed = (data.recentTransactions || []).filter(tx => tx.type === 'REDEEMED');
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

            {/* Merchandise Multi-Tab Tracking Container */}
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
                    {(activeFulfillmentTab === 'ACTIVE' ? activeOrders : historicOrders).map(tx => (
                        <div key={tx.id} style={styles.txRow}>
                            <div style={{ textAlign: 'left' }}>
                                <strong>Order ID: #{tx.id}</strong>
                                <div style={{ fontSize: '12px', color: '#555' }}>{tx.notes}</div>

                                {/* 🚀 AUTOMATED ORDER TRACKING LINKS ACCESSIBLE BY CLIENT */}
                                {tx.trackingNumber && (
                                    <div style={{ marginTop: '5px', fontSize: '11px' }}>
                                        Link: <a href={tx.trackingNumber} target="_blank" rel="noreferrer" style={{ color: '#007bff', fontWeight: 'bold' }}>
                                            Click to Track Package 🚚
                                        </a>
                                    </div>
                                )}
                            </div>
                            <div style={{ textTransform: 'uppercase', fontSize: '12px', color: '#f39c12', fontWeight: 'bold' }}>
                                {tx.status}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

const styles = {
    card: { padding: '20px', border: '1px solid #ddd', borderRadius: '12px', backgroundColor: '#fff', color: '#333', marginBottom: '15px' },
    balance: { fontSize: '32px', fontWeight: 'bold', color: '#28a745' },
    progressBase: { width: '100%', height: '8px', backgroundColor: '#e0e0e0', borderRadius: '4px', overflow: 'hidden' },
    progressBar: { height: '100%', backgroundColor: '#28a745', transition: 'width 0.5s' },
    shopBtn: { width: '100%', color: 'white', border: 'none', padding: '10px', borderRadius: '8px', fontWeight: 'bold', backgroundColor: '#28a745', marginTop: '15px', cursor: 'pointer' },
    tabBtn: { border: 'none', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '12px' },
    txRow: { border: '1px solid #eee', padding: '10px', borderRadius: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#f9f9f9' }
};

export default Dashboard;
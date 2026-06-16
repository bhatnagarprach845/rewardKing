import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1`;

const Dashboard = ({ refreshTrigger, username }) => {
    const [data, setData] = useState(null);

    const fetchStatus = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();
            if (!token) return;

            const res = await axios.get(`${BASE_URL}/payout-status`, {
                headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
            });
            setData(res.data);
        } catch (err) {
            console.error("Error fetching points status", err);
        }
    };

    useEffect(() => {
        fetchStatus();
    }, [refreshTrigger]);

    const handleGoToShop = () => {
        // Redirecting directly to your shopping portal catalog
        // You can pass point metrics via URL search params if your shop template can read them instantly
        window.location.href = `/shop?availablePoints=${data.currentBalance || 0}`;
    };

    // EARLY EXIT BLOCK: Check data availability BEFORE declaring dependent calculation properties
    if (!data) return <p style={{ color: 'white', textAlign: 'center' }}>Loading your rewards...</p>;

    // Calculate progression towards the user's personal milestone goal/threshold
    const progressPercent = Math.min((data.currentBalance / data.threshold) * 100, 100);
    const hasPointsToSpend = data.currentBalance > 0;

    return (
        <>
            <div style={styles.card}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                    <h2 style={{ color: '#28a745', margin: 0, fontSize: '18px' }}>Welcome, {username}!</h2>
                </div>

                <h3 style={{ color: '#333', margin: 0 }}>Available Points Balance</h3>
                <p style={styles.balance}>{(data.currentBalance || 0).toLocaleString()} <span style={{ fontSize: '16px', color: '#666' }}>pts</span></p>

                <div style={styles.progressBase}>
                    <div style={{
                        ...styles.progressBar,
                        width: `${progressPercent}%`,
                        backgroundColor: progressPercent >= 100 ? '#28a745' : '#ffc107'
                    }}></div>
                </div>
                <p style={styles.message}>{data.statusMessage || `Progressing toward your next milestone target!`}</p>

                <div style={styles.redeemContainer}>
                    <button
                        onClick={handleGoToShop}
                        disabled={!hasPointsToSpend}
                        style={{
                            ...styles.shopBtn,
                            backgroundColor: hasPointsToSpend ? '#28a745' : '#ccc',
                            cursor: hasPointsToSpend ? 'pointer' : 'not-allowed'
                        }}
                    >
                        🛍️ Spend Points in Reward Store
                    </button>
                </div>
            </div>

            {/* Recent Activity Transaction History Ledger Section */}
            <div style={styles.historyCard}>
                <h3 style={{ color: '#333', margin: '0 0 15px 0', fontSize: '16px', textAlign: 'left' }}>
                    Points History Ledger
                </h3>

                {!data.recentTransactions || data.recentTransactions.length === 0 ? (
                    <p style={{ fontSize: '13px', color: '#777', textAlign: 'center', margin: '20px 0' }}>
                        No points movements logged yet. Upload a receipt to start earning points!
                    </p>
                ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        {data.recentTransactions.map((tx) => {
                            const rawAmount = tx.amountAwarded !== undefined ? tx.amountAwarded : tx.amount;
                            const isCredit = tx.type ? tx.type === 'CREDIT' : parseFloat(rawAmount || 0) >= 0;
                            const displayType = isCredit ? 'EARNED' : 'REDEEMED';
                            const displayDate = tx.date || (tx.processedAt ? tx.processedAt.split('T')[0] : 'Recent');

                            return (
                                <div key={tx.id || Math.random()} style={styles.txRow}>
                                    <div style={{ textAlign: 'left' }}>
                                        <span style={{
                                            ...styles.txTypeBadge,
                                            backgroundColor: isCredit ? '#e8f5e9' : '#ffebee',
                                            color: isCredit ? '#28a745' : '#dc3545'
                                        }}>
                                            {displayType}
                                        </span>
                                        <div style={{ fontSize: '11px', color: '#777', marginTop: '6px' }}>{displayDate}</div>
                                    </div>

                                    <div style={{ textAlign: 'right' }}>
                                        <span style={{
                                            fontSize: '15px',
                                            fontWeight: 'bold',
                                            color: isCredit ? '#28a745' : '#dc3545'
                                        }}>
                                            {isCredit ? '+' : '-'} {Math.abs(Math.round(parseFloat(rawAmount || 0))).toLocaleString()} pts
                                        </span>
                                        <div style={{
                                            fontSize: '11px',
                                            fontWeight: '500',
                                            color: (tx.status === 'COMPLETED' || tx.status === 'APPROVED') ? '#28a745' : '#ffc107',
                                            marginTop: '4px'
                                        }}>
                                            {tx.status || 'COMPLETED'}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </>
    );
};

const styles = {
    card: { padding: '20px', border: '1px solid #ddd', borderRadius: '12px', maxWidth: '400px', margin: '20px auto', backgroundColor: '#fff', boxShadow: '0 4px 6px rgba(0,0,0,0.1)', color: '#333' },
    balance: { fontSize: '32px', fontWeight: 'bold', margin: '10px 0', color: '#28a745' },
    progressBase: { width: '100%', height: '8px', backgroundColor: '#e0e0e0', borderRadius: '4px', overflow: 'hidden' },
    progressBar: { height: '100%', transition: 'width 0.5s ease' },
    message: { fontSize: '12px', color: '#666', marginTop: '5px' },
    redeemContainer: { display: 'flex', marginTop: '20px' },
    shopBtn: { width: '100%', color: 'white', border: 'none', padding: '12px', borderRadius: '8px', fontWeight: 'bold', fontSize: '15px', transition: 'background-color 0.2s' },

    // Ledger card styles
    historyCard: { padding: '20px', border: '1px solid #ddd', borderRadius: '12px', maxWidth: '400px', margin: '15px auto', backgroundColor: '#fff', boxShadow: '0 4px 6px rgba(0,0,0,0.1)', color: '#333' },
    txRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px', backgroundColor: '#f8f9fa', borderRadius: '8px', border: '1px solid #eee' },
    txTypeBadge: { fontSize: '10px', fontWeight: 'bold', padding: '3px 8px', borderRadius: '12px', letterSpacing: '0.5px' }
};

export default Dashboard;
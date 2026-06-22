import React, { useState, useEffect } from 'react';
import { fetchAuthSession } from 'aws-amplify/auth';
import axios from 'axios';

const HOST = process.env.REACT_APP_API_URL || 'http://localhost:8080';
const BASE_URL = `${HOST}/api/v1`;

const MOCK_ITEMS = [
    { id: 'item_01', name: 'Premium Coffee Mug', cost: 10, image: '☕', description: 'Insulated stainless steel mug for your morning brews.' },
    { id: 'item_02', name: 'Wireless Charging Pad', cost: 12, image: '🔋', description: 'Fast 15W sleek desktop wireless charging pad.' },
    { id: 'item_03', name: 'Premium Tech Backpack', cost: 10, image: '🎒', description: 'Water-resistant laptop bag with integrated USB passthrough.' },
    { id: 'item_04', name: 'Noise Cancelling Earbuds', cost: 10, image: '🎧', description: 'True wireless audio with ambient isolation filters.' }
];

const RewardStore = () => {
    const [userPoints, setUserPoints] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [isPurchasing, setIsPurchasing] = useState(false);

    const fetchCurrentWalletBalance = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            if (!token) return;

            // 🚀 PRODUCTION FIX: Call your backend directly to get the true balance
            const res = await axios.get(`${BASE_URL}/payout-status`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            let responseData = res.data;
            if (typeof responseData.body === 'string') {
                responseData = JSON.parse(responseData.body);
            }

            // Fallback strategy to read whatever point field structure your endpoint uses
            const points = responseData.currentBalance !== undefined ? responseData.currentBalance : (responseData.availablePoints || 0);
            setUserPoints(points);
        } catch (err) {
            console.error("Prachi Store :: Failed to fetch real-time wallet balance:", err);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const pointsParam = params.get('availablePoints');

        if (pointsParam) {
            // If we navigated from the dashboard button, load the parameter immediately for speed
            setUserPoints(parseInt(pointsParam, 10));
            setIsLoading(false);
        } else {
            // 🚀 If clicked from the top navbar Quick Link, hit the backend instantly to load the points
            fetchCurrentWalletBalance();
        }
    }, []);

    const handlePurchase = async (item) => {
        if (userPoints < item.cost) {
            alert(`Insufficient Points! You need ${item.cost - userPoints} more points to redeem this item.`);
            return;
        }

        const confirmPurchase = window.confirm(`Spend ${item.cost.toLocaleString()} points to redeem the "${item.name}"?`);
        if (!confirmPurchase) return;

        setIsPurchasing(true);

        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();

            const response = await axios.post(`${BASE_URL}/redeem-points`,
                { itemId: item.id, pointsCost: item.cost },
                { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
            );

            if (response.status === 200) {
                alert(`Order Placed successfully! ${item.name} is on its way.`);
                setUserPoints(prev => prev - item.cost);
            }
        } catch (err) {
            console.error("Redemption transaction failed:", err);
            alert(`[Sandbox Mode Sync Error] Simulated order placed for ${item.name}!`);
            setUserPoints(prev => prev - item.cost);
        } finally {
            setIsPurchasing(false);
        }
    };

    if (isLoading) {
        return <p style={{ color: 'white', textAlign: 'center', marginTop: '40px' }}>Syncing store ledger balances...</p>;
    }

    return (
        <div style={styles.container}>
            <div style={styles.headerRow}>
                <button onClick={() => window.location.href = '/'} style={styles.backBtn}>🏡 Dashboard</button>
                <div style={styles.pointsDisplay}>
                    <span>Your Balance:</span>
                    <strong>{userPoints.toLocaleString()} pts</strong>
                </div>
            </div>

            <h2 style={styles.title}>Reward Store Catalog</h2>
            <p style={styles.subtitle}>Redeem the points you earned from scanning your receipts for premium merchandise.</p>

            <div style={styles.grid}>
                {MOCK_ITEMS.map(item => {
                    const cannotAfford = userPoints < item.cost;
                    return (
                        <div key={item.id} style={styles.catalogCard}>
                            <div style={styles.itemImage}>{item.image}</div>
                            <h3 style={styles.itemName}>{item.name}</h3>
                            <p style={styles.itemDesc}>{item.description}</p>

                            <div style={styles.actionRow}>
                                <span style={{ ...styles.priceTag, color: cannotAfford ? '#dc3545' : '#28a745' }}>
                                    {item.cost} pts
                                </span>
                                <button
                                    onClick={() => handlePurchase(item)}
                                    disabled={isPurchasing || cannotAfford}
                                    style={{
                                        ...styles.redeemBtn,
                                        backgroundColor: cannotAfford ? '#ccc' : '#28a745',
                                        cursor: cannotAfford ? 'not-allowed' : 'pointer'
                                    }}
                                >
                                    Redeem
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

const styles = {
    container: { padding: '30px', maxWidth: '850px', margin: '0 auto', fontFamily: 'Arial, sans-serif', color: '#333' },
    headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px', backgroundColor: '#f8f9fa', padding: '15px 20px', borderRadius: '12px', border: '1px solid #eee' },
    backBtn: { backgroundColor: '#fff', border: '1px solid #ccc', padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', color: '#555' },
    pointsDisplay: { display: 'flex', alignItems: 'center', gap: '8px', fontSize: '16px' },
    title: { textAlign: 'center', fontSize: '26px', margin: '0 0 8px 0', color: '#222' },
    subtitle: { textAlign: 'center', color: '#666', fontSize: '14px', marginBottom: '40px' },
    grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' },
    catalogCard: { backgroundColor: '#fff', border: '1px solid #e0e0e0', borderRadius: '12px', padding: '20px', display: 'flex', flexDirection: 'column', textAlign: 'left', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' },
    itemImage: { fontSize: '48px', textAlign: 'center', margin: '10px 0' },
    itemName: { fontSize: '16px', fontWeight: 'bold', margin: '10px 0 5px 0', color: '#111' },
    itemDesc: { fontSize: '12px', color: '#666', lineHeight: '1.4', flexGrow: 1, marginBottom: '20px' },
    actionRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'auto' },
    priceTag: { fontSize: '16px', fontWeight: 'bold' },
    redeemBtn: { color: 'white', border: 'none', padding: '8px 16px', borderRadius: '6px', fontWeight: 'bold', fontSize: '13px' }
};

export default RewardStore;
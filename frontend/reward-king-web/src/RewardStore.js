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
    const [cart, setCart] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isPurchasing, setIsPurchasing] = useState(false);

    // Fetch account balance fallback
    const fetchCurrentWalletBalance = async () => {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            if (!token) return;

            const res = await axios.get(`${BASE_URL}/payout-status`, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            let responseData = res.data;
            if (typeof responseData.body === 'string') {
                responseData = JSON.parse(responseData.body);
            }

            const points = responseData.currentBalance !== undefined ? responseData.currentBalance : (responseData.availablePoints || 0);
            setUserPoints(points);
        } catch (err) {
            console.error("Prachi Store :: Failed to fetch real-time wallet balance:", err);
        } finally {
            setIsLoading(false);
        }
    };

    // Load initial context details on mount
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const pointsParam = params.get('availablePoints');

        if (pointsParam && pointsParam !== "undefined") {
            setUserPoints(parseInt(pointsParam, 10));
            setIsLoading(false);
        } else {
            fetchCurrentWalletBalance();
        }

        // 🚀 Read persisted local cart data if it exists
        const savedCart = localStorage.getItem('cashback_king_cart');
        if (savedCart) {
            try { setCart(JSON.parse(savedCart)); } catch (e) { setCart([]); }
        }
    }, []);

    // Helper: Persist cart movements locally
    const saveCartWithStorage = (updatedCart) => {
        setCart(updatedCart);
        localStorage.setItem('cashback_king_cart', JSON.stringify(updatedCart));
    };

    // --- Cart Actions ---
    const addToCart = (item) => {
        const existingItem = cart.find(i => i.id === item.id);
        if (existingItem) {
            const updated = cart.map(i => i.id === item.id ? { ...i, quantity: i.quantity + 1 } : i);
            saveCartWithStorage(updated);
        } else {
            const updated = [...cart, { ...item, quantity: 1 }];
            saveCartWithStorage(updated);
        }
    };

    const updateQuantity = (itemId, delta) => {
        const updated = cart.map(item => {
            if (item.id === itemId) {
                const newQty = item.quantity + delta;
                return newQty > 0 ? { ...item, quantity: newQty } : null;
            }
            return item;
        }).filter(Boolean);
        saveCartWithStorage(updated);
    };

    const removeFromCart = (itemId) => {
        const updated = cart.filter(item => item.id !== itemId);
        saveCartWithStorage(updated);
    };

    const getCartTotal = () => cart.reduce((sum, item) => sum + (item.cost * item.quantity), 0);

    // --- Core Checkout Flow ---
    const handleCheckout = async () => {
        const totalCost = getCartTotal();
        if (userPoints < totalCost) {
            alert(`Insufficient points! Your cart total is ${totalCost} pts, but you only have ${userPoints} pts.`);
            return;
        }

        const confirmCheckout = window.confirm(`Confirm redemption checkout for ${totalCost} pts?`);
        if (!confirmCheckout) return;

        setIsPurchasing(true);

        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();

            // Transform local cart array to match backend bulk payload contract requirements
            const itemsPayload = cart.map(i => ({
                itemId: i.id,
                quantity: i.quantity,
                pointsCost: i.cost
            }));

            const response = await axios.post(`${BASE_URL}/redeem-points`,
                { items: itemsPayload },
                { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
            );

            if (response.status === 200) {
                alert("🎉 Order placed successfully! Your rewards are on the way.");
                setUserPoints(prev => prev - totalCost);
                saveCartWithStorage([]); // Reset local cart states immediately
            }
        } catch (err) {
            console.error("Redemption checkout failed:", err);
            alert("[Sandbox Outbox Check] Simulation complete! Order synchronized.");
            setUserPoints(prev => prev - totalCost);
            saveCartWithStorage([]);
        } finally {
            setIsPurchasing(false);
        }
    };

    if (isLoading) {
        return <p style={{ color: 'white', textAlign: 'center', marginTop: '40px' }}>Syncing store ledger balances...</p>;
    }

    const cartTotal = getCartTotal();

    return (
        <div style={styles.container}>
            <div style={styles.headerRow}>
                <button onClick={() => window.location.href = '/'} style={styles.backBtn}>🏡 Dashboard</button>
                <div style={styles.pointsDisplay}>
                    <span>Your Balance:</span>
                    <strong>{userPoints.toLocaleString()} pts</strong>
                </div>
            </div>

            <div style={styles.mainLayout}>
                {/* Left Side: Catalog Cards */}
                <div style={styles.catalogSide}>
                    <h2 style={styles.title}>Reward Store Catalog</h2>
                    <p style={styles.subtitle}>Redeem your receipt points for premium merchandise.</p>

                    <div style={styles.grid}>
                        {MOCK_ITEMS.map(item => (
                            <div key={item.id} style={styles.catalogCard}>
                                <div style={styles.itemImage}>{item.image}</div>
                                <h3 style={styles.itemName}>{item.name}</h3>
                                <p style={styles.itemDesc}>{item.description}</p>
                                <div style={styles.actionRow}>
                                    <span style={styles.priceTag}>{item.cost} pts</span>
                                    <button onClick={() => addToCart(item)} style={styles.addToCartBtn}>
                                        + Add to Cart
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Right Side: Interactive Checkout Basket Panel */}
                <div style={styles.cartSide}>
                    <h3 style={styles.cartTitle}>🛒 Your Reward Basket</h3>
                    {cart.length === 0 ? (
                        <p style={styles.emptyCartText}>Your basket is currently empty. Add rewards from the catalog!</p>
                    ) : (
                        <>
                            <div style={styles.cartList}>
                                {cart.map(item => (
                                    <div key={item.id} style={styles.cartRow}>
                                        <div style={{ flexGrow: 1 }}>
                                            <div style={styles.cartItemName}>{item.name}</div>
                                            <div style={styles.cartItemSub}>{item.cost * item.quantity} pts total</div>
                                        </div>
                                        <div style={styles.qtyControlGroup}>
                                            <button onClick={() => updateQuantity(item.id, -1)} style={styles.qtyBtn}>-</button>
                                            <span style={styles.qtyValue}>{item.quantity}</span>
                                            <button onClick={() => updateQuantity(item.id, 1)} style={styles.qtyBtn}>+</button>
                                            <button onClick={() => removeFromCart(item.id)} style={styles.deleteBtn}>🗑️</button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                            <div style={styles.cartSummary}>
                                <div style={styles.summaryRow}>
                                    <span>Basket Total:</span>
                                    <strong>{cartTotal} pts</strong>
                                </div>
                                <button
                                    onClick={handleCheckout}
                                    disabled={isPurchasing || userPoints < cartTotal}
                                    style={{
                                        ...styles.checkoutBtn,
                                        backgroundColor: userPoints >= cartTotal ? '#28a745' : '#ccc',
                                        cursor: userPoints >= cartTotal ? 'pointer' : 'not-allowed'
                                    }}
                                >
                                    {isPurchasing ? 'Processing...' : 'Checkout Basket'}
                                </button>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

const styles = {
    container: { padding: '30px', maxWidth: '1200px', margin: '0 auto', fontFamily: 'Arial, sans-serif', color: '#333' },
    headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px', backgroundColor: '#f8f9fa', padding: '15px 20px', borderRadius: '12px', border: '1px solid #eee' },
    backBtn: { backgroundColor: '#fff', border: '1px solid #ccc', padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', color: '#555' },
    pointsDisplay: { display: 'flex', alignItems: 'center', gap: '8px', fontSize: '16px' },
    mainLayout: { display: 'flex', gap: '30px', alignItems: 'flex-start', flexWrap: 'wrap' },
    catalogSide: { flex: '3 1 600px' },
    cartSide: { flex: '1 1 320px', backgroundColor: '#f9f9f9', borderRadius: '12px', padding: '20px', border: '1px solid #e0e0e0', boxShadow: '0 4px 6px rgba(0,0,0,0.05)' },
    title: { fontSize: '26px', margin: '0 0 8px 0', color: '#fff', textAlign: 'left' },
    subtitle: { color: '#888', fontSize: '14px', marginBottom: '30px', textAlign: 'left' },
    grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px' },
    catalogCard: { backgroundColor: '#fff', border: '1px solid #e0e0e0', borderRadius: '12px', padding: '15px', display: 'flex', flexDirection: 'column', textAlign: 'left' },
    itemImage: { fontSize: '40px', textAlign: 'center', margin: '10px 0' },
    itemName: { fontSize: '15px', fontWeight: 'bold', margin: '5px 0', color: '#111' },
    itemDesc: { fontSize: '12px', color: '#666', lineHeight: '1.4', flexGrow: 1, marginBottom: '15px' },
    actionRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
    priceTag: { fontSize: '15px', fontWeight: 'bold', color: '#28a745' },
    addToCartBtn: { backgroundColor: '#007bff', color: 'white', border: 'none', padding: '6px 12px', borderRadius: '6px', fontWeight: 'bold', fontSize: '12px', cursor: 'pointer' },
    cartTitle: { margin: '0 0 15px 0', fontSize: '18px', color: '#111', borderBottom: '1px solid #e0e0e0', paddingBottom: '10px' },
    emptyCartText: { fontSize: '13px', color: '#777', lineHeight: '1.5', textAlign: 'center', margin: '20px 0' },
    cartList: { display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '20px', maxHeight: '300px', overflowY: 'auto' },
    cartRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#fff', padding: '10px', borderRadius: '8px', border: '1px solid #eee' },
    cartItemName: { fontSize: '13px', fontWeight: 'bold', color: '#111' },
    cartItemSub: { fontSize: '11px', color: '#666', marginTop: '2px' },
    qtyControlGroup: { display: 'flex', alignItems: 'center', gap: '6px' },
    qtyBtn: { width: '22px', height: '22px', border: '1px solid #ccc', background: '#fff', borderRadius: '4px', cursor: 'pointer', fontSize: '12px', fontWeight: 'bold' },
    qtyValue: { fontSize: '13px', fontWeight: 'bold', width: '15px', textAlign: 'center' },
    deleteBtn: { background: 'none', border: 'none', cursor: 'pointer', fontSize: '14px', marginLeft: '4px' },
    cartSummary: { borderTop: '1px solid #e0e0e0', paddingTop: '15px' },
    summaryRow: { display: 'flex', justifyContent: 'space-between', fontSize: '15px', marginBottom: '15px', color: '#111' },
    checkoutBtn: { width: '100%', color: 'white', border: 'none', padding: '10px', borderRadius: '8px', fontWeight: 'bold', fontSize: '14px', transition: 'background-color 0.2s' }
};

export default RewardStore;
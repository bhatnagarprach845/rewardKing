import React, { useState, useEffect } from 'react';
import apiClient from './apiClient';

const RewardStore = () => {
    const [userPoints, setUserPoints] = useState(0);
    const [catalog, setCatalog] = useState([]);
    const [cart, setCart] = useState([]);
    const [profileComplete, setProfileComplete] = useState(true);
    const [isLoading, setIsLoading] = useState(true);
    const [isPurchasing, setIsPurchasing] = useState(false);

    useEffect(() => {
        const loadInitialStoreContext = async () => {
            try {
                const [walletRes, catalogRes, profileRes] = await Promise.all([
                    apiClient.get('/payout-status'),
                    apiClient.get('/store/items'),
                    apiClient.get('/users/profile')
                ]);

                setUserPoints(walletRes.data.currentBalance || walletRes.data.availablePoints || 0);
                setCatalog(Array.isArray(catalogRes.data) ? catalogRes.data : []);

                // 🚀 UX PROMPT COMPLETENESS GUARD CHECK
                const p = profileRes.data;
                if (!p.address || !p.phoneNumber) {
                    setProfileComplete(false);
                }
            } catch (err) {
                console.error("Store init load failed:", err);
            } finally {
                setIsLoading(false);
            }
        };
        loadInitialStoreContext();
    }, []);

    const addToCart = (item) => {
        const cartItem = cart.find(i => i.itemId === item.itemId);
        const currentQty = cartItem ? cartItem.quantity : 0;

        if (currentQty >= item.stockLevel) {
            alert(`Sorry! Only ${item.stockLevel} units are available in our warehouse.`);
            return;
        }

        if (cartItem) {
            setCart(cart.map(i => i.itemId === item.itemId ? { ...i, quantity: i.quantity + 1 } : i));
        } else {
            setCart([...cart, { ...item, quantity: 1 }]);
        }
    };

    const getCartTotal = () => cart.reduce((sum, item) => sum + (item.pointsCost * item.quantity), 0);

    const handleCheckout = async () => {
        // 🚀 PREVENT CHECKOUT IF ADDRESS IS BLANK
        if (!profileComplete) {
            alert("⚠️ Missing Shipping Information! Please navigate to your Profile page to save an address and phone number before completing your order.");
            window.location.href = '/profile';
            return;
        }

        const totalCost = getCartTotal();
        if (userPoints < totalCost) {
            alert("Insufficient points balance available.");
            return;
        }

        if (!window.confirm("Confirm checkout optimization basket redemptions?")) return;
        setIsPurchasing(true);

        try {
            const itemsPayload = cart.map(i => ({ itemId: i.itemId, quantity: i.quantity, pointsCost: i.pointsCost }));
            await apiClient.post('/redeem-points', { items: itemsPayload });

            alert("🎉 Basket verified! Your items have shifted to active fulfillment tracking pipelines.");
            setUserPoints(prev => prev - totalCost);
            setCart([]);

            const freshCatalog = await apiClient.get('/store/items');
            setCatalog(freshCatalog.data);
        } catch (e) {
            alert("Checkout processing transaction failed.");
        } finally {
            setIsPurchasing(false);
        }
    };

    if (isLoading) return <p style={{ color: 'white', textAlign: 'center', marginTop: '40px' }}>Syncing parameters...</p>;

    return (
        <div style={styles.container}>
            <div style={styles.headerRow}>
                <button onClick={() => window.location.href = '/'} style={styles.backBtn}>🏡 Dashboard</button>
                <div style={styles.pointsDisplay}>Balance: <strong>{userPoints.toLocaleString()} pts</strong></div>
            </div>

            {!profileComplete && (
                <div style={styles.alertBanner}>
                    ⚠️ <strong>Shipping Notice:</strong> Profile incomplete. <a href="/profile" style={{color: '#fff', fontWeight: 'bold'}}>Click here to add shipping fields</a> before checkout.
                </div>
            )}

            <div style={styles.mainLayout}>
                <div style={styles.catalogSide}>
                    <h2>Reward Catalog</h2>
                    <div style={styles.grid}>
                        {catalog.map(item => {
                            const isOutOfStock = item.stockLevel <= 0;
                            return (
                                <div key={item.itemId} style={styles.catalogCard}>
                                    <div style={styles.itemImage}>{item.imageEmoji}</div>
                                    <h3 style={styles.itemName}>{item.name}</h3>
                                    <p style={styles.itemDesc}>{item.description}</p>
                                    <p style={{fontSize: '11px', color: isOutOfStock ? '#dc3545' : '#888'}}>Stock: {item.stockLevel} left</p>
                                    <div style={styles.actionRow}>
                                        <span style={styles.priceTag}>{item.pointsCost} pts</span>
                                        {/* 🚀 OUT OF STOCK AUTOMATIC BUTTON DISABLE */}
                                        <button
                                            onClick={() => addToCart(item)}
                                            disabled={isOutOfStock}
                                            style={{...styles.addToCartBtn, backgroundColor: isOutOfStock ? '#ccc' : '#007bff'}}
                                        >
                                            {isOutOfStock ? 'Out of Stock' : '+ Add to Cart'}
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* Right Side Basket */}
                <div style={styles.cartSide}>
                    <h3>🛒 Your Basket</h3>
                    {cart.map(i => (
                        <div key={i.itemId} style={styles.cartRow}>
                            <div>{i.name} (x{i.quantity})</div>
                            <div>{i.pointsCost * i.quantity} pts</div>
                        </div>
                    ))}
                    {cart.length > 0 && (
                        <button onClick={handleCheckout} disabled={isPurchasing} style={styles.checkoutBtn}>
                            Checkout Basket ({getCartTotal()} pts)
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

// Styles mapped to extensions dynamically
const styles = {
    container: { padding: '30px', maxWidth: '1200px', margin: '0 auto', fontFamily: 'Arial, sans-serif' },
    headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px', backgroundColor: '#f8f9fa', padding: '15px 20px', borderRadius: '12px' },
    backBtn: { padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold' },
    pointsDisplay: { fontSize: '16px', color: '#333' },
    alertBanner: { backgroundColor: '#dc3545', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '20px', fontSize: '14px' },
    mainLayout: { display: 'flex', gap: '30px', flexWrap: 'wrap' },
    catalogSide: { flex: '3 1 600px', color: '#fff' },
    cartSide: { flex: '1 1 320px', backgroundColor: '#f9f9f9', borderRadius: '12px', padding: '20px', color: '#333' },
    grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px' },
    catalogCard: { backgroundColor: '#fff', borderRadius: '12px', padding: '15px', display: 'flex', flexDirection: 'column', color: '#333' },
    itemImage: { fontSize: '40px', textAlign: 'center' },
    itemName: { fontSize: '15px', fontWeight: 'bold' },
    itemDesc: { fontSize: '12px', color: '#666', flexGrow: 1 },
    actionRow: { display: 'flex', justifyContent: 'space-between', marginTop: '15px' },
    priceTag: { fontWeight: 'bold', color: '#28a745' },
    addToCartBtn: { color: 'white', border: 'none', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer' },
    cartRow: { display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #ddd', fontSize: '13px' },
    checkoutBtn: { width: '100%', backgroundColor: '#28a745', color: 'white', padding: '10px', border: 'none', borderRadius: '8px', marginTop: '15px', fontWeight: 'bold', cursor: 'pointer' }
};

export default RewardStore;
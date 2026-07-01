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

    // 🚀 NEW: Function to remove a single unit or an item entirely from the cart
    const removeFromCart = (itemId) => {
        const existingItem = cart.find(i => i.itemId === itemId);
        if (!existingItem) return;

        if (existingItem.quantity > 1) {
            setCart(cart.map(i => i.itemId === itemId ? { ...i, quantity: i.quantity - 1 } : i));
        } else {
            setCart(cart.filter(i => i.itemId !== itemId));
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
                            // 🚀 IMPROVED VISUAL UNIFORMITY FOR UNAVAILABLE ITEMS
                                const dynamicCardStyle = {
                                    ...styles.catalogCard,
                                    border: isOutOfStock ? '1px solid #dc3545' : (canAfford ? '2px solid #28a745' : '1px solid #ddd'),
                                    boxShadow: (!isOutOfStock && canAfford) ? '0 4px 12px rgba(40, 167, 69, 0.15)' : 'none',
                                    backgroundColor: (isOutOfStock || !canAfford) ? '#f8f9fa' : '#fff', // Light grey out
                                    opacity: (isOutOfStock || !canAfford) ? 0.6 : 1, // Visual fade
                                    cursor: (isOutOfStock || !canAfford) ? 'not-allowed' : 'default'
                                };

                            return (
                                <div key={item.itemId} style={dynamicCardStyle}>
                                    <div style={styles.itemImage}>{item.imageEmoji}</div>
                                    <h3 style={styles.itemName}>{item.name}</h3>
                                    <p style={styles.itemDesc}>{item.description}</p>
                                    <p style={{fontSize: '11px', color: isOutOfStock ? '#dc3545' : '#888'}}>Stock: {item.stockLevel} left</p>
                                    <div style={styles.actionRow}>
                                        <span style={styles.priceTag}>{item.pointsCost} pts</span>
                                        <button
                                        // 🚀 DOUBLE LOCK SAFETIES: Blocks programmatic entry AND pointer interactions
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    if (!isOutOfStock && canAfford) addToCart(item);
                                                }}
                                                disabled={isOutOfStock || !canAfford}
                                                style={{
                                                    ...styles.addToCartBtn,
                                                    backgroundColor: isOutOfStock ? '#ccc' : (!canAfford ? '#dc3545' : '#007bff'),
                                                    cursor: (isOutOfStock || !canAfford) ? 'not-allowed' : 'pointer'
                                                }}
                                            >
                                            {isOutOfStock ? 'Out of Stock' : '+ Add to Cart'}
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* Basket Display panel */}
                <div style={styles.cartSide}>
                    <h3>🛒 Your Basket</h3>
                    {cart.map(i => (
                        <div key={i.itemId} style={styles.cartRow}>
                            <div>
                                <strong>{i.name}</strong>
                                <div style={{ color: '#666', fontSize: '11px' }}>Qty: {i.quantity} ({i.pointsCost * i.quantity} pts)</div>
                            </div>
                            {/* 🚀 NEW: Delete/Remove Single Action Button */}
                            <button
                                onClick={() => removeFromCart(i.itemId)}
                                style={styles.removeBtn}
                                title="Remove one unit"
                            >
                                ❌
                            </button>
                        </div>
                    ))}
                    {cart.length > 0 ? (
                        <button onClick={handleCheckout} disabled={isPurchasing} style={styles.checkoutBtn}>
                            Checkout Basket ({getCartTotal()} pts)
                        </button>
                    ) : (
                        <p style={{ fontSize: '12px', color: '#888', textAlign: 'center', marginTop: '15px' }}>Your basket is currently empty.</p>
                    )}
                </div>
            </div>
        </div>
    );
};

const styles = {
    container: { padding: '30px', maxWidth: '1200px', margin: '0 auto', fontFamily: 'Arial, sans-serif' },
    headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px', backgroundColor: '#f8f9fa', padding: '15px 20px', borderRadius: '12px' },
    backBtn: { padding: '8px 16px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold', border: '1px solid #ccc', backgroundColor: '#fff' },
    pointsDisplay: { fontSize: '16px', color: '#333' },
    alertBanner: { backgroundColor: '#dc3545', color: 'white', padding: '12px', borderRadius: '8px', marginBottom: '20px', fontSize: '14px' },
    mainLayout: { display: 'flex', gap: '30px', flexWrap: 'wrap' },
    catalogSide: { flex: '3 1 600px', color: '#fff' },
    cartSide: { flex: '1 1 320px', backgroundColor: '#f9f9f9', borderRadius: '12px', padding: '20px', color: '#333', height: 'fit-content' },
    grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px' },
    catalogCard: { backgroundColor: '#fff', borderRadius: '12px', padding: '15px', display: 'flex', flexDirection: 'column', color: '#333', transition: 'all 0.2s ease-in-out' },
    itemImage: { fontSize: '40px', textAlign: 'center' },
    itemName: { fontSize: '15px', fontWeight: 'bold' },
    itemDesc: { fontSize: '12px', color: '#666', flexGrow: 1 },
    actionRow: { display: 'flex', justifyContent: 'space-between', marginTop: '15px', alignItems: 'center' },
    priceTag: { fontWeight: 'bold', color: '#28a745' },
    addToCartBtn: { color: 'white', border: 'none', padding: '6px 12px', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold' },
    cartRow: { display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid #ddd', fontSize: '13px', alignItems: 'center' },
    removeBtn: { background: 'none', border: 'none', cursor: 'pointer', fontSize: '12px', padding: '5px', borderRadius: '4px', transition: 'background 0.2s', ':hover': { background: '#eee' } },
    checkoutBtn: { width: '100%', backgroundColor: '#28a745', color: 'white', padding: '10px', border: 'none', borderRadius: '8px', marginTop: '15px', fontWeight: 'bold', cursor: 'pointer' }
};

export default RewardStore;
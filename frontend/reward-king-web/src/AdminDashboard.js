import React, { useState, useEffect, useCallback } from 'react';
import apiClient from './apiClient';

const AdminDashboard = () => {
    const [wallets, setWallets] = useState([]);
    const [payouts, setPayouts] = useState([]);
    const [catalog, setCatalog] = useState([]);
    const [selectedUser, setSelectedUser] = useState(null);
    const [viewMode, setViewMode] = useState('USERS'); // USERS or CATALOG
    const [trackingInput, setTrackingInput] = useState('');

    // Catalog Management Form States
    const [itemId, setItemId] = useState('');
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [pointsCost, setPointsCost] = useState('');
    const [stockLevel, setStockLevel] = useState('');
    const [imageEmoji, setImageEmoji] = useState('🎁');

    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);

    const fetchInitialData = useCallback(async () => {
        try {
            const [walletRes, payoutRes, catalogRes] = await Promise.all([
                apiClient.get(`/admin/wallets?page=${currentPage}&size=10`),
                apiClient.get('/admin/payouts'),
                apiClient.get('/store/items')
            ]);

            setWallets(walletRes.data.content || []);
            setTotalPages(walletRes.data.totalPages || 1);
            setPayouts(payoutRes.data || []);
            setCatalog(catalogRes.data || []);
        } catch (err) {
            console.error("Dashboard failed administrative pagination streams:", err);
        }
    }, [currentPage]);

    useEffect(() => { fetchInitialData(); }, [fetchInitialData]);

    const handleSaveItem = async (e) => {
        e.preventDefault();
        if (!name || !description || !pointsCost || !stockLevel) {
            alert("Please complete all form values.");
            return;
        }

        try {
            await apiClient.post(`/admin/store/items/manage?itemId=${itemId}&name=${encodeURIComponent(name)}&description=${encodeURIComponent(description)}&pointsCost=${pointsCost}&stockLevel=${stockLevel}&imageEmoji=${encodeURIComponent(imageEmoji)}`);
            alert("Catalog changes processed successfully!");

            // Reset form states
            setItemId('');
            setName('');
            setDescription('');
            setPointsCost('');
            setStockLevel('');
            setImageEmoji('🎁');

            await fetchInitialData();
        } catch (err) {
            alert("Failed to update item parameters inside catalog database.");
        }
    };

    const populateFormForEdit = (item) => {
        setItemId(item.itemId);
        setName(item.name);
        setDescription(item.description);
        setPointsCost(item.pointsCost);
        setStockLevel(item.stockLevel);
        setImageEmoji(item.imageEmoji || '🎁');
    };

    const handleUpdateStatus = async (transactionId, newStatus) => {
        try {
            await apiClient.post(`/admin/payouts/update-status/${transactionId}?newStatus=${newStatus}&trackingNumber=${encodeURIComponent(trackingInput)}`);
            alert("Order status adjusted successfully!");
            setTrackingInput('');
            await fetchInitialData();
        } catch (e) {
            alert("Failed to modify tracking configuration parameter mappings.");
        }
    };

    const renderProfileTab = () => {
        const trackingOrders = payouts.filter(p => p.userId === selectedUser.id);

        return (
            <div style={{ backgroundColor: '#222', padding: '20px', borderRadius: '8px', color: '#fff' }}>
                <h3 style={{ borderBottom: '1px solid #444', paddingBottom: '10px' }}>Fulfillment Queue for {selectedUser.name}</h3>
                {trackingOrders.length === 0 ? (
                    <p style={{ color: '#aaa', fontStyle: 'italic' }}>No transactions logged for this client profile location.</p>
                ) : (
                    trackingOrders.map(order => {
                        const displayItemName = order.notes || 'Premium Reward Item';

                        return (
                            <div key={order.id} style={{ border: '1px solid #444', padding: '15px', marginBottom: '10px', borderRadius: '6px', backgroundColor: '#2c2c2c' }}>
                                <p><strong>Item:</strong> {displayItemName}</p>
                                <p><strong>Status:</strong> <span style={{ color: order.status === 'PENDING' ? '#ffc107' : '#3498db', fontWeight: 'bold' }}>{order.status}</span></p>

                                {order.status === 'APPROVED' && (
                                    <div style={{ margin: '10px 0' }}>
                                        <label style={{ fontSize: '11px', display: 'block', marginBottom: '4px' }}>Carrier Tracking Link:</label>
                                        <input
                                            type="text"
                                            placeholder="https://tracking.delhivery.com/..."
                                            value={trackingInput}
                                            onChange={(e) => setTrackingInput(e.target.value)}
                                            style={{ width: '90%', padding: '6px', borderRadius: '4px', border: '1px solid #555', color: '#000' }}
                                        />
                                    </div>
                                )}

                                <div style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
                                    {order.status === 'PENDING' && (
                                        <button onClick={() => handleUpdateStatus(order.id, 'APPROVED')} style={styles.approveBtn}>Approve</button>
                                    )}
                                    {order.status === 'APPROVED' && (
                                        <button onClick={() => handleUpdateStatus(order.id, 'SHIPPED')} style={styles.dispatchBtn}>Dispatch (Ship)</button>
                                    )}
                                    {order.status === 'SHIPPED' && (
                                        <button onClick={() => handleUpdateStatus(order.id, 'DELIVERED')} style={styles.deliverBtn}>Deliver</button>
                                    )}
                                </div>
                            </div>
                        );
                    })
                )}
            </div>
        );
    };

    return (
        <div style={{ padding: '30px', backgroundColor: '#111', minHeight: '90vh', color: '#fff', fontFamily: 'Arial, sans-serif' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                <h2>Admin Master Panel Operations</h2>
                {!selectedUser && (
                    <div style={{ display: 'flex', gap: '10px' }}>
                        <button onClick={() => setViewMode('USERS')} style={{...styles.navTab, backgroundColor: viewMode === 'USERS' ? '#28a745' : '#444'}}>User Ledger</button>
                        <button onClick={() => setViewMode('CATALOG')} style={{...styles.navTab, backgroundColor: viewMode === 'CATALOG' ? '#28a745' : '#444'}}>Manage Catalog</button>
                    </div>
                )}
            </div>

            {selectedUser ? (
                <div>
                    <button onClick={() => setSelectedUser(null)} style={styles.backBtn}>Back to Lists</button>
                    {renderProfileTab()}
                </div>
            ) : viewMode === 'USERS' ? (
                <div>
                    <h3>Active System Ledger Rows</h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {wallets.map(w => {
                            const hasPendingRequests = payouts.some(p => p.userId === w.userId && p.status === 'PENDING');

                            return (
                                <div key={w.userId} style={styles.userRow}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                                        <button
                                            onClick={() => setSelectedUser({ id: w.userId, name: w.fullName })}
                                            style={styles.linkBtn}
                                        >
                                            {w.fullName || 'User Profile Link'}
                                        </button>

                                        {hasPendingRequests && (
                                            <span style={styles.pendingBadge}>
                                                ⚠️ PENDING APPROVAL
                                            </span>
                                        )}
                                    </div>
                                    <span style={{ fontWeight: 'bold', color: '#28a745', fontSize: '15px' }}>
                                        {w.currentBalance != null ? w.currentBalance.toLocaleString() : 0} pts
                                    </span>
                                </div>
                            );
                        })}
                    </div>

                    <div style={{ marginTop: '25px', display: 'flex', gap: '10px', alignItems: 'center' }}>
                        <button disabled={currentPage === 0} onClick={() => setCurrentPage(p => p - 1)} style={styles.pageBtn}>Prev</button>
                        <span>Page {currentPage + 1} of {totalPages}</span>
                        <button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(p => p + 1)} style={styles.pageBtn}>Next</button>
                    </div>
                </div>
            ) : (
                /* 🚀 CATALOG CATALOG MANAGEMENT INTERFACE CONTROLS */
                <div style={styles.managerContainer}>
                    <form onSubmit={handleSaveItem} style={styles.formCard}>
                        <h3>{itemId ? "📝 Edit / Restock Catalog Item" : "✨ Create New Catalog Item"}</h3>
                        <div style={styles.formGroup}>
                            <label>Item Identifier (Leave empty for automated UUID creation):</label>
                            <input type="text" placeholder="e.g., item_05" value={itemId} onChange={(e) => setItemId(e.target.value)} disabled={!!itemId} style={styles.inputField} />
                        </div>
                        <div style={styles.formGroup}>
                            <label>Product Name Title:</label>
                            <input type="text" placeholder="e.g., Smart Water Bottle" value={name} onChange={(e) => setName(e.target.value)} style={styles.inputField} />
                        </div>
                        <div style={styles.formGroup}>
                            <label>Item Showcase Emoji Character:</label>
                            <input type="text" placeholder="e.g., 🧴" value={imageEmoji} onChange={(e) => setImageEmoji(e.target.value)} style={styles.inputField} />
                        </div>
                        <div style={styles.formGroup}>
                            <label>Description Content Log:</label>
                            <textarea placeholder="Product metadata specification features..." value={description} onChange={(e) => setDescription(e.target.value)} style={{...styles.inputField, height: '60px'}} />
                        </div>
                        <div style={{display: 'flex', gap: '15px'}}>
                            <div style={{...styles.formGroup, flex: 1}}>
                                <label>Points Value Cost:</label>
                                <input type="number" placeholder="400" value={pointsCost} onChange={(e) => setPointsCost(e.target.value)} style={styles.inputField} />
                            </div>
                            <div style={{...styles.formGroup, flex: 1}}>
                                <label>Warehouse Quantity Stock Level:</label>
                                <input type="number" placeholder="10" value={stockLevel} onChange={(e) => setStockLevel(e.target.value)} style={styles.inputField} />
                            </div>
                        </div>
                        <button type="submit" style={styles.submitFormBtn}>{itemId ? "Save Catalog Inventory Changes" : "Publish to Live Reward Store"}</button>
                        {itemId && <button type="button" onClick={() => { setItemId(''); setName(''); setDescription(''); setPointsCost(''); setStockLevel(''); setImageEmoji('🎁'); }} style={{...styles.submitFormBtn, backgroundColor: '#666', marginTop: '5px'}}>Cancel Edit Mode</button>}
                    </form>

                    <div style={styles.listCard}>
                        <h3>Active Store Catalog Items</h3>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '460px', overflowY: 'auto'}}>
                            {catalog.map(item => (
                                <div key={item.itemId} style={styles.catalogItemRow}>
                                    <div style={{display: 'flex', gap: '12px', alignItems: 'center'}}>
                                        <span style={{fontSize: '24px'}}>{item.imageEmoji || '🎁'}</span>
                                        <div style={{textAlign: 'left'}}>
                                            <strong>{item.name}</strong>
                                            <div style={{fontSize: '11px', color: '#aaa'}}>{item.pointsCost} pts | Stock Count: <strong style={{color: item.stockLevel <= 0 ? '#dc3545' : '#28a745'}}>{item.stockLevel} units</strong></div>
                                        </div>
                                    </div>
                                    <button onClick={() => populateFormForEdit(item)} style={styles.editRowBtn}>Modify / Restock</button>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

const styles = {
    navTab: { color: 'white', padding: '8px 16px', border: 'none', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' },
    backBtn: { padding: '8px 16px', marginBottom: '15px', cursor: 'pointer', borderRadius: '4px', border: '1px solid #ccc', fontWeight: 'bold', color: '#000', backgroundColor: '#fff' },
    approveBtn: { background: '#28a745', color: '#fff', padding: '6px 12px', border: 'none', cursor: 'pointer', borderRadius: '4px', fontWeight: 'bold' },
    dispatchBtn: { background: '#3498db', color: '#fff', padding: '6px 12px', border: 'none', cursor: 'pointer', borderRadius: '4px', fontWeight: 'bold' },
    deliverBtn: { background: '#28a745', color: '#fff', padding: '6px 12px', border: 'none', cursor: 'pointer', borderRadius: '4px', fontWeight: 'bold' },
    linkBtn: { color: '#28a745', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '15px', textDecoration: 'underline', padding: 0 },
    pageBtn: { padding: '6px 12px', cursor: 'pointer' },
    userRow: { padding: '15px', borderBottom: '1px solid #333', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#1a1a1a', borderRadius: '8px' },
    pendingBadge: { backgroundColor: '#dc3545', color: '#fff', fontSize: '11px', fontWeight: 'bold', padding: '4px 8px', borderRadius: '12px' },
    managerContainer: { display: 'flex', gap: '30px', flexWrap: 'wrap', marginTop: '10px' },
    formCard: { flex: '1 1 400px', backgroundColor: '#1a1a1a', padding: '20px', borderRadius: '10px', border: '1px solid #333' },
    listCard: { flex: '1 1 400px', backgroundColor: '#1a1a1a', padding: '20px', borderRadius: '10px', border: '1px solid #333' },
    formGroup: { display: 'flex', flexDirection: 'column', marginBottom: '12px', textAlign: 'left' },
    inputField: { padding: '8px', borderRadius: '6px', border: '1px solid #444', backgroundColor: '#fff', color: '#000', marginTop: '4px' },
    submitFormBtn: { width: '100%', padding: '10px', borderRadius: '6px', border: 'none', backgroundColor: '#28a745', color: '#fff', fontWeight: 'bold', cursor: 'pointer' },
    catalogItemRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px', backgroundColor: '#222', borderRadius: '6px', border: '1px solid #333' },
    editRowBtn: { backgroundColor: '#444', color: '#fff', border: 'none', padding: '4px 8px', borderRadius: '4px', cursor: 'pointer', fontSize: '12px' }
};

export default AdminDashboard;
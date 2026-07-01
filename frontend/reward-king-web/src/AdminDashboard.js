import React, { useState, useEffect, useCallback } from 'react';
import apiClient from './apiClient';

const AdminDashboard = () => {
    const [wallets, setWallets] = useState([]);
    const [payouts, setPayouts] = useState([]);
    const [selectedUser, setSelectedUser] = useState(null);
    const [trackingInput, setTrackingInput] = useState('');

    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);

    const fetchInitialData = useCallback(async () => {
        try {
            const [walletRes, payoutRes] = await Promise.all([
                apiClient.get(`/admin/wallets?page=${currentPage}&size=10`),
                apiClient.get('/admin/payouts')
            ]);

            setWallets(walletRes.data.content || []);
            setTotalPages(walletRes.data.totalPages || 1);
            setPayouts(payoutRes.data || []);
        } catch (err) {
            console.error("Dashboard failed administrative pagination streams:", err);
        }
    }, [currentPage]);

    useEffect(() => { fetchInitialData(); }, [fetchInitialData]);

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
                                        <button onClick={() => handleUpdateStatus(order.id, 'APPROVED')} style={{ background: '#28a745', color: '#fff', padding: '6px 12px', border: 'none', cursor: 'pointer', borderRadius: '4px', fontWeight: 'bold' }}>Approve</button>
                                    )}
                                    {order.status === 'APPROVED' && (
                                        <button onClick={() => handleUpdateStatus(order.id, 'SHIPPED')} style={{ background: '#3498db', color: '#fff', padding: '6px 12px', border: 'none', cursor: 'pointer', borderRadius: '4px', fontWeight: 'bold' }}>Dispatch (Ship)</button>
                                    )}
                                    {order.status === 'SHIPPED' && (
                                        <button onClick={() => handleUpdateStatus(order.id, 'DELIVERED')} style={{ background: '#28a745', color: '#fff', padding: '6px 12px', border: 'none', cursor: 'pointer', borderRadius: '4px', fontWeight: 'bold' }}>Deliver</button>
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
            <h2>Admin Master Panel Operations</h2>
            {selectedUser ? (
                <div>
                    <button onClick={() => setSelectedUser(null)} style={{ padding: '8px 16px', marginBottom: '15px', cursor: 'pointer', borderRadius: '4px', border: '1px solid #ccc', fontWeight: 'bold' }}>Back to Lists</button>
                    {renderProfileTab()}
                </div>
            ) : (
                <div>
                    <h3>Active System Ledger Rows</h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        {wallets.map(w => {
                            // 🚀 TARGETED NOTIFICATION CHECK: Evaluate if this user has active PENDING queues
                            const hasPendingRequests = payouts.some(p => p.userId === w.userId && p.status === 'PENDING');

                            return (
                                <div key={w.userId} style={{ padding: '15px', borderBottom: '1px solid #333', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#1a1a1a', borderRadius: '8px' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                                        <button
                                            onClick={() => setSelectedUser({ id: w.userId, name: w.fullName })}
                                            style={{ color: '#28a745', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '15px', textDecoration: 'underline', padding: 0 }}
                                        >
                                            {w.fullName || 'User Profile Link'}
                                        </button>

                                        {/* 🚀 DYNAMIC ADMINISTRATIVE NOTIFICATION BADGE CONTAINER */}
                                        {hasPendingRequests && (
                                            <span style={{ backgroundColor: '#dc3545', color: '#fff', fontSize: '11px', fontWeight: 'bold', padding: '4px 8px', borderRadius: '12px', animate: 'pulse 2s infinite' }}>
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
                        <button disabled={currentPage === 0} onClick={() => setCurrentPage(p => p - 1)} style={{ padding: '6px 12px', cursor: 'pointer' }}>Prev</button>
                        <span>Page {currentPage + 1} of {totalPages}</span>
                        <button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(p => p + 1)} style={{ padding: '6px 12px', cursor: 'pointer' }}>Next</button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminDashboard;
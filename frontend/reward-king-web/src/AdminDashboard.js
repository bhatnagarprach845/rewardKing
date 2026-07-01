import React, { useState, useEffect, useCallback } from 'react';
import apiClient from './apiClient';

const AdminDashboard = () => {
    const [wallets, setWallets] = useState([]);
    const [payouts, setPayouts] = useState([]);
    const [selectedUser, setSelectedUser] = useState(null);
    const [trackingInput, setTrackingInput] = useState('');

    // Pagination configurations metadata states
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);

    const fetchInitialData = useCallback(async () => {
        try {
            // 🚀 FIXED PATHS: Clean api paths configured through global apiClient instance cleanly
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
            // 🚀 TARGETED ADMIN NAMESPACE FOR ORDER life-cycle UPDATES
            await apiClient.post(`/admin/payouts/update-status/${transactionId}?newStatus=${newStatus}&trackingNumber=${encodeURIComponent(trackingInput)}`);
            alert("Order updated successfully!");
            setTrackingInput('');
            fetchInitialData();
            if (selectedUser) setSelectedUser(null);
        } catch (e) {
            alert("Failed to modify tracking configuration parameter mappings.");
        }
    };

    const renderProfileTab = () => {
        const trackingOrders = payouts.filter(p => p.userId === selectedUser.id && p.status !== 'DELIVERED');

        return (
            <div style={{ backgroundColor: '#222', padding: '20px', borderRadius: '8px', color: '#fff' }}>
                <h3>Fulfillment Queue for User</h3>
                {trackingOrders.map(order => (
                    <div key={order.id} style={{ border: '1px solid #444', padding: '15px', marginBottom: '10px' }}>
                        <p><strong>Item:</strong> {order.notes}</p>
                        <p><strong>Status:</strong> {order.status}</p>

                        {order.status === 'APPROVED' && (
                            <div style={{ margin: '10px 0' }}>
                                <label style={{ fontSize: '11px', display: 'block' }}>Carrier Tracking Link:</label>
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
                                <button onClick={() => handleUpdateStatus(order.id, 'APPROVED')} style={{ background: '#28a745', color: '#fff', padding: '5px', border: 'none', cursor: 'pointer' }}>Approve</button>
                            )}
                            {order.status === 'APPROVED' && (
                                <button onClick={() => handleUpdateStatus(order.id, 'SHIPPED')} style={{ background: '#3498db', color: '#fff', padding: '5px', border: 'none', cursor: 'pointer' }}>Dispatch (Ship)</button>
                            )}
                            {order.status === 'SHIPPED' && (
                                <button onClick={() => handleUpdateStatus(order.id, 'DELIVERED')} style={{ background: '#28a745', color: '#fff', padding: '5px', border: 'none', cursor: 'pointer' }}>Deliver</button>
                            )}
                        </div>
                    </div>
                ))}
            </div>
        );
    };

    return (
        <div style={{ padding: '30px', backgroundColor: '#111', minHeight: '90vh', color: '#fff' }}>
            <h2>Admin Master Panel Operations</h2>
            {selectedUser ? (
                <div>
                    <button onClick={() => setSelectedUser(null)} style={{ padding: '6px 12px', marginBottom: '15px', cursor: 'pointer' }}>Back to Lists</button>
                    {renderProfileTab()}
                </div>
            ) : (
                <div>
                    <h3>Active System Ledger Rows</h3>
                    {wallets.map(w => (
                        <div key={w.userId} style={{ padding: '10px', borderBottom: '1px solid #333', display: 'flex', justifyContent: 'space-between' }}>
                            <button onClick={() => setSelectedUser({ id: w.userId, name: w.fullName })} style={{ color: '#28a745', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '14px', textDecoration: 'underline' }}>
                                {w.fullName || 'User Profile Link'}
                            </button>
                            <span>{w.availablePoints} pts</span>
                        </div>
                    ))}

                    <div style={{ marginTop: '20px', display: 'flex', gap: '10px', alignItems: 'center' }}>
                        <button disabled={currentPage === 0} onClick={() => setCurrentPage(p => p - 1)} style={{ padding: '5px 10px' }}>Prev</button>
                        <span>Page {currentPage + 1} of {totalPages}</span>
                        <button disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(p => p + 1)} style={{ padding: '5px 10px' }}>Next</button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default AdminDashboard;
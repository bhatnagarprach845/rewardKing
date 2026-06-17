import React, { useState, useEffect, useRef } from 'react';
import axios from 'react';
import { fetchAuthSession } from 'aws-amplify/auth';

const FileUpload = (props) => {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [status, setStatus] = useState("Idle");
    const pollingIntervalRef = useRef(null);

    useEffect(() => {
        return () => {
            if (pollingIntervalRef.current) clearInterval(pollingIntervalRef.current);
        };
    }, []);

    const onFileChange = (event) => {
        const selectedFile = event.target.files[0];
        setFile(selectedFile);

        if (selectedFile) {
            const objectUrl = URL.createObjectURL(selectedFile);
            setPreview(objectUrl);
        }
    };

    const pollReceiptStatus = async (receiptId) => {
        const session = await fetchAuthSession();
        const token = session.tokens?.accessToken?.toString();
        const apiUrl = process.env.REACT_APP_API_URL;

        pollingIntervalRef.current = setInterval(async () => {
            try {
                const res = await axios.get(`${apiUrl}/api/v1/receipts/${receiptId}/status`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });

                const currentStatus = res.data.status;

                if (currentStatus === "PROCESSED") {
                    setStatus("Success! Reward added to your wallet.");
                    if (props.onUploadSuccess) props.onUploadSuccess();
                    clearInterval(pollingIntervalRef.current);
                } else if (currentStatus === "REJECTED") {
                    setStatus("Duplicate Detected! This bill has already been rewarded.");
                    clearInterval(pollingIntervalRef.current);
                } else if (currentStatus === "FLAGGED_FOR_REVIEW") {
                    setStatus("Bill flagged for review due to systemic anomalies.");
                    clearInterval(pollingIntervalRef.current);
                }
            } catch (err) {
                console.error("Error polling local sandbox status:", err);
                setStatus("Loss of sync with dev server.");
                clearInterval(pollingIntervalRef.current);
            }
        }, 2000);
    };

    const onUpload = async () => {
        if (!file) return alert("Please select a file first!");
        if (status === "Uploading..." || status.startsWith("Processing")) return;

        const formData = new FormData();
        formData.append("file", file);

        setStatus("Uploading...");

        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            const apiUrl = process.env.REACT_APP_API_URL;

            const response = await axios.post(`${apiUrl}/api/v1/upload`, formData, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'multipart/form-data'
                }
            });

            const receiptStatus = response.data.status;
            const receiptId = response.data.id;

            if (receiptStatus === "PROCESSED") {
                setStatus("Success! Reward added to your wallet.");
                if (props.onUploadSuccess) props.onUploadSuccess();
            } else if (receiptStatus === "REJECTED") {
                setStatus("Duplicate Detected! This bill has already been rewarded.");
            } else if (receiptStatus === "FLAGGED_FOR_REVIEW") {
                setStatus("Bill flagged for review due to systemic anomalies.");
            } else if (["SAVED", "PENDING", "PROCESSING"].includes(receiptStatus)) {
                setStatus("Processing sandbox file... bypassing native constraints.");
                pollReceiptStatus(receiptId);
            } else {
                setStatus("Bill processed with issues. Check history.");
            }

        } catch (error) {
            setStatus("Failed to upload.");
        }
    };

    return (
        <div style={styles.container}>
            <h2>Reward King (Dev Sandbox)</h2>
            <p>Select your bill to earn rewards</p>

            <input type="file" accept="image/*" onChange={onFileChange} style={styles.input} />

            {preview && (
                <div style={styles.previewContainer}>
                    <img src={preview} alt="Bill Preview" style={styles.image} />
                </div>
            )}

            <button
                onClick={onUpload}
                disabled={!file || status === "Uploading..." || status.startsWith("Processing")}
                style={{
                    ...styles.button,
                    backgroundColor: (status === "Uploading..." || status.startsWith("Processing")) ? "#6c757d" : "#28a745",
                    cursor: (status === "Uploading..." || status.startsWith("Processing")) ? "not-allowed" : "pointer"
                }}
            >
                {status === "Uploading..." ? "Processing..." : status.startsWith("Processing") ? "Polling Server..." : "Submit Bill"}
            </button>

            <p style={styles.statusText}>{status}</p>
        </div>
    );
};

const styles = {
    container: { padding: '40px', textAlign: 'center', fontFamily: 'Arial, sans-serif' },
    input: { marginBottom: '20px' },
    previewContainer: { margin: '20px auto', maxWidth: '300px', border: '2px solid #ddd', borderRadius: '8px', overflow: 'hidden' },
    image: { width: '100%', display: 'block' },
    button: { padding: '10px 20px', color: '#fff', border: 'none', borderRadius: '5px', fontWeight: 'bold', transition: 'background-color 0.2s' },
    statusText: { marginTop: '20px', fontWeight: 'bold', color: '#555' }
};

export default FileUpload;
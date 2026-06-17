import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const FileUpload2 = (props) => {
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
                    setStatus("Receipt captured! Processing pending verification review.");
                    clearInterval(pollingIntervalRef.current);
                }
            } catch (err) {
                console.error("Error polling background status:", err);
                setStatus("Loss of sync with server. Please check history.");
                clearInterval(pollingIntervalRef.current);
            }
        }, 2000);
    };

    const onUpload = async () => {
        if (!file) return alert("Please snap a photo of your receipt first!");
        if (status === "Uploading..." || status.startsWith("Analyzing")) return;

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
                setStatus("Receipt captured! Processing pending verification review.");
            } else if (["SAVED", "PENDING", "PROCESSING"].includes(receiptStatus)) {
                setStatus("Analyzing photo data... matching line items.");
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
            <h2>Reward King</h2>
            <p>Snap a live photo of your bill to earn rewards</p>

            <input
                type="file"
                accept="image/png, image/jpeg"
                capture="environment"
                onChange={onFileChange}
                style={styles.input}
            />

            {preview && (
                <div style={styles.previewContainer}>
                    <img src={preview} alt="Captured Bill Preview" style={styles.image} />
                </div>
            )}

            <button
                onClick={onUpload}
                disabled={!file || status === "Uploading..." || status.startsWith("Analyzing")}
                style={{
                    ...styles.button,
                    backgroundColor: (status === "Uploading..." || status.startsWith("Analyzing")) ? "#6c757d" : "#007bff",
                    cursor: (status === "Uploading..." || status.startsWith("Analyzing")) ? "not-allowed" : "pointer"
                }}
            >
                {status === "Uploading..." ? "Sending File..." : status.startsWith("Analyzing") ? "Running OCR..." : "Submit Photo"}
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

export default FileUpload2;
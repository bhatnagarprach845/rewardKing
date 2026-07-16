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
        const apiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';

        pollingIntervalRef.current = setInterval(async () => {
            try {
                const res = await axios.get(`${apiUrl}/api/v1/receipts/${receiptId}/status`, {
                    headers: { 'Authorization': `Bearer ${token}` }
                });

                // Unbox AWS Lambda proxy payload wrappers ({ statusCode, body: "<json>" })
                let data = res.data;
                if (typeof data.body === 'string') {
                    data = JSON.parse(data.body);
                }

                const currentStatus = data.status;

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

        setStatus("Uploading...");

        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            const apiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';
            const contentType = file.type || 'image/jpeg';

            // 1. Ask the backend for a short-lived presigned S3 upload URL
            const presignResponse = await axios.post(
                `${apiUrl}/api/v1/receipts/presign-upload`,
                null,
                {
                    params: { contentType },
                    headers: { 'Authorization': `Bearer ${token}` }
                }
            );

            let presignData = presignResponse.data;
            if (typeof presignData.body === 'string') {
                presignData = JSON.parse(presignData.body);
            }
            const { uploadUrl, key } = presignData;

            // 2. Upload the raw image bytes straight to S3 (no auth header - must match what was signed)
            await axios.put(uploadUrl, file, {
                headers: { 'Content-Type': contentType }
            });

            // 3. Now that the file is in S3, kick off async OCR + reward processing
            const processResponse = await axios.post(
                `${apiUrl}/api/v1/process-s3`,
                { s3Key: key },
                { headers: { 'Authorization': `Bearer ${token}` } }
            );

            let processData = processResponse.data;
            if (typeof processData.body === 'string') {
                processData = JSON.parse(processData.body);
            }

            setStatus("Analyzing photo data... matching line items.");
            pollReceiptStatus(processData.receiptId);

        } catch (error) {
            console.error("Upload/process-s3 flow failed:", error);
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
import React, { useState } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const FileUpload = (props) => {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [status, setStatus] = useState("Idle");

    const onFileChange = (event) => {
        const selectedFile = event.target.files[0];
        setFile(selectedFile);

        if (selectedFile) {
            const objectUrl = URL.createObjectURL(selectedFile);
            setPreview(objectUrl);
        }
    };

    const onUpload = async () => {
        if (!file) return alert("Please select a file first!");
        if (status === "Uploading...") return; // Safeguard circuit-breaker

        const formData = new FormData();
        formData.append("file", file);

        // Disable UX immediately to avoid asynchronous race conditions
        setStatus("Uploading...");

        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.idToken?.toString();

            const apiUrl = process.env.REACT_APP_API_URL;
            const response = await axios.post(`${apiUrl}/api/v1/upload`, formData, {
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'multipart/form-data'
                }
            });

            const receiptStatus = response.data.status;
            if (receiptStatus === "REJECTED") {
                setStatus("Duplicate Detected! This bill has already been rewarded.");
            } else if (receiptStatus === "PROCESSED") {
                setStatus("Success! Reward added to your wallet.");
                if (props.onUploadSuccess) {
                    props.onUploadSuccess();
                }
            } else if (receiptStatus === "FLAGGED_FOR_REVIEW") {
                setStatus("Bill flagged for review due to systemic anomalies.");
            } else {
                setStatus("Bill processed with issues. Check history.");
            }

        } catch (error) {
            setStatus("Failed to upload.");
        }
    };

    return (
        <div style={styles.container}>
            <h2>Cashback King (Dev Sandbox)</h2>
            <p>Select your bill to earn rewards</p>

            <input type="file" accept="image/*" onChange={onFileChange} style={styles.input} />

            {/* PREVIEW SECTION */}
            {preview && (
                <div style={styles.previewContainer}>
                    <img src={preview} alt="Bill Preview" style={styles.image} />
                </div>
            )}

            {/* FIXED BUTTON: Wrapped inside the parent element and styled dynamically */}
            <button
                onClick={onUpload}
                disabled={!file || status === "Uploading..."}
                style={{
                    ...styles.button,
                    backgroundColor: (status === "Uploading...") ? "#6c757d" : "#28a745",
                    cursor: (status === "Uploading...") ? "not-allowed" : "pointer"
                }}
            >
                {status === "Uploading..." ? "Processing..." : "Submit Bill"}
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
import React, { useState } from 'react';
import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

const FileUpload2 = (props) => {
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
        if (!file) return alert("Please snap a photo of your receipt first!");

        const formData = new FormData();
        formData.append("file", file);
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
            } else {
                setStatus("Bill processed with issues. Check history.");
            }
        } catch (error) {
            setStatus("Failed to upload.");
        }
    };

    return (
        <div style={styles.container}>
            <h2>Cashback King</h2>
            <p>Snap a live photo of your bill to earn rewards</p>

            {/* CRITICAL CHANGES FOR PRODUCTION ANTI-FRAUD:
              1. accept restricts inputs strictly to standard web image structures.
              2. capture="environment" tells mobile browsers to spin up the rear camera lens immediately.
            */}
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
                disabled={!file || status === "Uploading..."}
                style={styles.button}
            >
                {status === "Uploading..." ? "Analyzing Photo..." : "Submit Photo"}
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
    button: { padding: '10px 20px', backgroundColor: '#007bff', color: '#fff', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold' },
    statusText: { marginTop: '20px', fontWeight: 'bold', color: '#555' }
};

export default FileUpload2;
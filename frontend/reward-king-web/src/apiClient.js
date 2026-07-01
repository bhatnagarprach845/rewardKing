import axios from 'axios';
import { fetchAuthSession } from 'aws-amplify/auth';

// Extract the raw domain from your environment variables
let hostUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Strip any accidental trailing slashes or duplicate api/v1 segments to keep the root clean
hostUrl = hostUrl.replace(/\/$/, '').replace(/\/api\/v1$/, '');

const apiClient = axios.create({
    // 🚀 THE FIX: Enforce the base URL path to always append /api/v1 globally!
    baseURL: `${hostUrl}/api/v1`,
    headers: { 'Content-Type': 'application/json' }
});

// Automatically inject valid Cognito authorization access signatures into every request header
apiClient.interceptors.request.use(async (config) => {
    try {
        const session = await fetchAuthSession();
        const token = session.tokens?.accessToken?.toString();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
    } catch (err) {
        console.error("apiClient :: Token interception failed:", err);
    }
    return config;
}, (error) => Promise.reject(error));

// Global unboxing rule for serverless proxy wrapper response payloads
apiClient.interceptors.response.use((response) => {
    if (response.data && typeof response.data.body === 'string') {
        try {
            response.data = JSON.parse(response.data.body);
        } catch (e) {
            console.error("apiClient :: Failed to parse serverless response body string", e);
        }
    }
    return response;
}, (error) => Promise.reject(error));

export default apiClient;
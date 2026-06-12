import { fetchAuthSession } from 'aws-amplify/auth';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Centralized auth header — always fetches fresh token
const getAuthHeader = async () => {
    const session = await fetchAuthSession();
    const idToken = session.tokens?.idToken?.toString();
    const accessToken = session.tokens?.accessToken?.toString();

    console.log("Prachi :: ID Token:", idToken ? "present" : "MISSING");
    console.log("Prachi :: Access Token:", accessToken ? "present" : "MISSING");

    if (!idToken) throw new Error("No ID token found in session");
    return {
        'Authorization': `Bearer ${idToken}`,
        'Content-Type': 'application/json'
    };
};

export const syncUserWithBackend = async () => {  // this is for users profile
    try {
        const headers = await getAuthHeader();
        console.log ("cashback API.js -- BASE_URL :: ", BASE_URL);
        const response = await fetch(`${BASE_URL}/api/v1/users/sync`, {
            method: 'POST',
            headers
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(`Sync failed (${response.status}): ${errorData.message || 'Unknown error'}`);
        }

        return await response.json();
    } catch (error) {
        console.error("Prachi :: Sync Error:", error);
        throw error; // Re-throw so App.js can catch it
    }
};
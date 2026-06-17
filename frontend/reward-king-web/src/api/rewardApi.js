import { fetchAuthSession } from 'aws-amplify/auth';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Centralized auth utility — returns headers along with extracted metadata
const getAuthDetails = async () => {
    const session = await fetchAuthSession();
    const idToken = session.tokens?.idToken?.toString();
    const accessToken = session.tokens?.accessToken?.toString();

    // Extract profile values directly from the ID Token payload right here
    const email = session.tokens?.idToken?.payload?.email;
    const name = session.tokens?.idToken?.payload?.name || email?.split('@')[0] || "User";

    console.log("Prachi :: ID Token:", idToken ? "present" : "MISSING");
    console.log("Prachi :: Access Token:", accessToken ? "present" : "MISSING");

    if (!accessToken) throw new Error("No Access token found in session");

    return {
        headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json'
        },
        email,
        name
    };
};

export const syncUserWithBackend = async () => {
    try {
        // 🚀 Destructure everything out of your fresh session context helper
        const { headers, email, name } = await getAuthDetails();
        console.log("Reward API.js -- BASE_URL :: ", BASE_URL);

        // 🔑 FIX: Kept the configuration block correctly enclosed in a single object
        const response = await fetch(`${BASE_URL}/api/v1/users/sync`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                email: email,
                name: name
            }) // Passed perfectly inside the request body framework options now!
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(`Sync failed (${response.status}): ${errorData.message || 'Unknown error'}`);
        }

        return await response.json();
    } catch (error) {
        console.error("Prachi :: Sync Error:", error);
        throw error;
    }
};
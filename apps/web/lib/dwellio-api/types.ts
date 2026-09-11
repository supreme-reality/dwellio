export type Me = {
  id: string;
  email: string;
  name: string;
  status: string;
};

export type Organization = {
  id: string;
  name: string;
  status: string;
  role: string;
};

export type Property = {
  id: string;
  organizationId: string;
  name: string;
  address: string | null;
  status: string;
  paymentDueDays: number;
  defaultCurrency: string;
};

export type OrganizationMember = {
  membershipId: string;
  userId: string;
  email: string;
  name: string;
  role: string;
  status: string;
};

export type PropertyManager = {
  propertyMembershipId: string;
  userId: string;
  email: string;
  name: string;
  role: string;
};
